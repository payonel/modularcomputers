-- ld.lua: the program interpreter, as an ELF program's (PT_INTERP: ld.so, with libc's start, crt1
-- and __libc_start_main). Every program is Lua, native to this CPU, and the kernel starts every
-- process here, with the program's path and its arguments: it opens the rest of the standard
-- library over the process's syscalls (io, os, print, require, package, loadfile, dofile), sets arg,
-- calls the program, and exits with what it returns (an integer), 1 if it fails (its error and stack
-- traceback on stderr). It's not a program itself: /bin/lua, Lua's standalone interpreter, is one.
local sys = sys
_G.sys = nil -- programs ask for it: require("sys")

local LIB = "/lib/" -- where the standard library's own parts are

-- A file as a function, as Lua's loadfile: its text is the kernel's shared image of it (compiled
-- once for every process running it), run with 'env' as its globals.
local function loadfile(path, mode, env)
  env = env or _G
  if path == nil then
    return load(io.read("a"), "=stdin", mode, env)
  end
  local image, why = sys.image(path)
  if not image then
    if why:sub(1, #path) ~= path then
      why = path .. ": " .. why
    end
    return nil, why
  end
  return function(...)
    return image(env, ...)
  end
end
_G.loadfile = loadfile

local ioload, why = loadfile(LIB .. "io.lua")
if not ioload then
  sys.write(2, "ld.lua: " .. tostring(why) .. "\n")
  sys.exit(1)
end
-- The environment: getenv's, setenv's, and what io.popen's children get.
local environ = sys.environ()
io = ioload(sys, environ)
-- (not a syscall, as C's fileno isn't: an io file's descriptor, for sys.poll. A drift from Lua's io,
-- which hides it; what the file has read ahead into its buffer, poll doesn't see, as with C's stdio)
function sys.fileno(file)
  return file.fd
end

-- os: what Lua's has that means something here.
os = {}
function os.getenv(name)
  return environ[name]
end
-- (not in Lua's os, as luaposix's setenv: the environment children get)
function os.setenv(name, value)
  environ[name] = value
end
function os.exit(code, close)
  io.flushall()
  sys.exit((code == nil or code == true) and 0 or code == false and 1 or code)
end
-- (C's remove: a file, a link itself (never what it points to), or an empty directory)
function os.remove(path)
  local st, why, errno = sys.lstat(path)
  if st then
    st, why, errno = (st.type == "directory" and sys.rmdir or sys.unlink)(path)
  end
  if not st then
    return nil, path .. ": " .. why, errno
  end
  return true
end
function os.rename(from, to)
  local ok, why, errno = sys.rename(from, to)
  if not ok then
    return nil, from .. ": " .. why, errno
  end
  return true
end
-- With no real-time clock the time is seconds since boot (a board without an RTC starts at the epoch).
function os.time(t)
  if t then
    error("os.time: dates aren't supported yet", 2)
  end
  return math.floor(sys.uptime())
end
function os.clock()
  return sys.uptime()
end
function os.difftime(a, b)
  return a - (b or 0)
end
local tmpcount = 0
function os.tmpname()
  tmpcount = tmpcount + 1
  return ("/tmp/lua_%d_%d"):format(sys.getpid(), tmpcount)
end

function print(...)
  local args = table.pack(...)
  for i = 1, args.n do
    args[i] = tostring(args[i])
  end
  io.stdout:write(table.concat(args, "\t", 1, args.n), "\n")
end

function dofile(path)
  local chunk = assert(loadfile(path))
  return chunk()
end

-- require and package, as Lua's: package.path's templates, package.loaded, package.preload.
package = {
  path = "/lib/?.lua;/lib/?/init.lua;/usr/lib/?.lua;./?.lua",
  loaded = {sys = sys, io = io, os = os, string = string, table = table, math = math, utf8 = utf8,
    coroutine = coroutine, _G = _G},
  preload = {},
  config = "/\n;\n?\n!\n-\n",
}
function package.searchpath(name, path, sep, rep)
  name = name:gsub("%" .. (sep or "."), rep or "/")
  local tried = {}
  for template in path:gmatch("[^;]+") do
    local file = template:gsub("%?", name)
    local st = sys.stat(file)
    if st and st.type ~= "directory" then
      return file
    end
    tried[#tried + 1] = "\n\tno file '" .. file .. "'"
  end
  return nil, table.concat(tried)
end
function require(name)
  local loaded = package.loaded[name]
  if loaded ~= nil then
    return loaded
  end
  local loader, extra = package.preload[name], ":preload:"
  if not loader then
    local file, tried = package.searchpath(name, package.path)
    if not file then
      error("module '" .. name .. "' not found:\n\tno field package.preload['" .. name .. "']" .. tried, 2)
    end
    local err
    loader, err = loadfile(file)
    if not loader then
      error("error loading module '" .. name .. "' from file '" .. file .. "':\n\t" .. err, 2)
    end
    extra = file
  end
  local value = loader(name, extra)
  if value == nil then
    value = package.loaded[name] == nil and true or package.loaded[name]
  end
  package.loaded[name] = value
  return value, extra
end

-- What an error object says: a string as it is, an object with __tostring through it, else
-- "(error object is a <type> value)", as Lua's interpreter says.
local function message(err)
  if type(err) == "string" or math.type(err) then
    return tostring(err)
  end
  local meta = getmetatable(err)
  if type(meta) == "table" and meta.__tostring then
    local ok, text = pcall(tostring, err) -- (it errs unless __tostring gives a string)
    if ok then
      return text
    end
  end
  return "(error object is a " .. type(err) .. " value)"
end

-- The stack where an error happened, as debug.traceback gives it (from the frame that raised it),
-- cut at this file's xpcall: what's below is the loader's and the kernel's, not the program's. (Its
-- is the last xpcall: a program's own are above it, and over by the time an error reaches this one.)
local function traceback()
  local text = debug.traceback("", 3)
  local cut
  for at in text:gmatch("()\n[^\n]*'xpcall'") do
    cut = at
  end
  text = cut and text:sub(1, cut - 1) or text
  return (text:gsub("\n[^\n]*%(%.%.%.tail calls%.%.%.%)$", ""))
end

local path = ...
local name = path:match("([^/]*)$") -- (as a program's messages start: program_invocation_short_name)
_G.arg = {[0] = path, select(2, ...)}
local status
local main, err = loadfile(path)
if not main then
  io.stderr:write(name, ": ", err, "\n")
  status = 1
else
  -- an error: its message and stack traceback on stderr, 1; an integer it returns: its status; false
  -- (or nil) and a message: 1, the message on stderr (as OpenOS took "return false, why"); else 0
  local ok, value, why = xpcall(main, function(e)
    return message(e) .. traceback()
  end, select(2, ...))
  if not ok then
    io.stdout:flush()
    io.stderr:write(name, ": ", value, "\n")
    status = 1
  elseif math.type(value) == "integer" then
    status = value
  elseif value == false or (value == nil and why ~= nil) then
    if why ~= nil then
      io.stdout:flush()
      io.stderr:write(message(why), "\n")
    end
    status = 1
  else
    status = 0
  end
end
io.flushall()
return status

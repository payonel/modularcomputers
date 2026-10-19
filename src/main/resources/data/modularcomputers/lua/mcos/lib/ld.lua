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
    if string.sub(why, 1, #path) ~= path then
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
io = ioload(sys)

-- os: what Lua's has that means something here.
local environ = sys.environ()
io.environ = function()
  local copy = {}
  for name, value in pairs(environ) do
    copy[name] = value
  end
  return copy
end

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
  if code == true or code == nil then
    code = 0
  elseif code == false then
    code = 1
  end
  sys.exit(code)
end
function os.remove(path)
  local st, why, errno = sys.stat(path)
  if not st then
    return nil, path .. ": " .. why, errno
  end
  local ok
  if st.type == "directory" then
    ok, why, errno = sys.rmdir(path)
  else
    ok, why, errno = sys.unlink(path)
  end
  if not ok then
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
  return string.format("/tmp/lua_%d_%d", sys.getpid(), tmpcount)
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
  name = string.gsub(name, "%" .. (sep or "."), rep or "/")
  local tried = {}
  for template in string.gmatch(path, "[^;]+") do
    local file = string.gsub(template, "%?", name)
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
    local ok, text = pcall(tostring, err)
    if ok and type(text) == "string" then
      return text
    end
  end
  return "(error object is a " .. type(err) .. " value)"
end

-- The stack where an error happened, as debug.traceback gives it (from the frame that raised it),
-- cut at this file's xpcall: what's below is the loader's and the kernel's, not the program's.
local function traceback()
  local lines = {}
  for line in string.gmatch(debug.traceback("", 3), "[^\n]+") do
    lines[#lines + 1] = line
  end
  local cut = #lines + 1
  for k = #lines, 1, -1 do
    if string.find(lines[k], "'xpcall'", 1, true) then
      cut = k
      break
    end
  end
  while cut > 2 and string.find(lines[cut - 1], "(...tail calls...)", 1, true) do
    cut = cut - 1
  end
  return "\n" .. table.concat(lines, "\n", 1, cut - 1)
end

local path = ...
local args = table.pack(select(2, ...))
local name = string.match(path, "([^/]*)$") -- (as a program's messages start: program_invocation_short_name)
_G.arg = {[0] = path, table.unpack(args, 1, args.n)}
local status
local main, err = loadfile(path)
if not main then
  io.stderr:write(name, ": ", err, "\n")
  status = 1
else
  -- an error: its message and stack traceback on stderr, 1; an integer it returns: its status; false
  -- (or nil) and a message: 1, the message on stderr (as OpenOS took "return false, why"); else 0
  local results = table.pack(xpcall(main, function(e)
    return {message(e) .. traceback()}
  end, table.unpack(args, 1, args.n)))
  if not results[1] then
    io.stdout:flush()
    io.stderr:write(name, ": ", results[2][1], "\n")
    status = 1
  elseif math.type(results[2]) == "integer" then
    status = results[2]
  elseif results[2] == false or (results[2] == nil and results.n >= 3 and results[3] ~= nil) then
    if results[3] ~= nil then
      io.stdout:flush()
      io.stderr:write(message(results[3]), "\n")
    end
    status = 1
  else
    status = 0
  end
end
io.flushall()
return status

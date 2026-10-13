-- mcos (Lua): the Modular Computers OS, booted from its disk (... is the disk's address).
local root = ...

mcos = {version = "0.1", root = root, cwd = "/"}

-- Paths: absolute, or from the working directory; "." and ".." as usual.
function mcos.resolve(path)
  if path:sub(1, 1) ~= "/" then
    path = mcos.cwd .. "/" .. path
  end
  local parts = {}
  for name in path:gmatch("[^/]+") do
    if name == ".." then
      parts[#parts] = nil
    elseif name ~= "." then
      parts[#parts + 1] = name
    end
  end
  return "/" .. table.concat(parts, "/")
end

-- Files on the boot disk.
local fs = {}
mcos.fs = fs

function fs.invoke(method, ...)
  local result = table.pack(board.invoke(root, method, ...))
  if not result[1] then
    return nil, result[2]
  end
  return table.unpack(result, 2, result.n)
end

function fs.read(path)
  local file, why = fs.invoke("open", path)
  if not file then
    return nil, why
  end
  local parts = {}
  while true do
    local ok, chunk = board.invoke(file, "read", 4096)
    if not ok or chunk == nil then
      break
    end
    parts[#parts + 1] = chunk
  end
  board.invoke(file, "close")
  return table.concat(parts)
end

function fs.write(path, data, append)
  local file, why = fs.invoke("open", path, append and "a" or "w")
  if not file then
    return nil, why
  end
  board.invoke(file, "write", data)
  local ok, err = board.invoke(file, "close")
  if not ok then
    return nil, err
  end
  return true
end

function fs.list(path)
  return fs.invoke("list", path)
end

function fs.stat(path)
  return fs.invoke("stat", path)
end

-- The console: writing, and reading a line with editing.
local term = {}
mcos.term = term

function term.write(text)
  board.invoke("console", "write", text)
end

function term.readLine(prompt)
  term.write(prompt or "")
  local line = ""
  while true do
    board.waitForInterrupt(nil, "console")
    while true do
      local _, key = board.invoke("console", "read")
      if key == nil then
        break
      end
      if key == "\r" or key == "\n" then
        term.write("\n")
        return line
      elseif key == "\127" or key == "\b" then
        if #line > 0 then
          line = line:sub(1, -2)
          term.write("\b \b")
        end
      elseif key == "\3" then
        term.write("^C\n")
        return nil
      elseif #key == 1 and key:byte() >= 32 then
        line = line .. key
        term.write(key)
      end
    end
  end
end

-- Modules from /lib, programs from /bin.
local loaded = {}
function require(name)
  if loaded[name] == nil then
    local path = "/lib/" .. name:gsub("%.", "/") .. ".lua"
    local code, why = fs.read(path)
    if not code then
      error("module '" .. name .. "' not found: " .. tostring(why), 2)
    end
    loaded[name] = assert(load(code, "=" .. path))(name) or true
  end
  return loaded[name]
end

function mcos.run(path, ...)
  local code, why = fs.read(path)
  if not code then
    return false, why
  end
  local program, err = load(code, "=" .. path)
  if not program then
    return false, err
  end
  return pcall(program, ...)
end

term.write("\27[0m\27[2J\27[H" .. "mcos (Lua) " .. mcos.version .. "\n")
local ok, why = mcos.run("/bin/sh.lua")
if not ok then
  term.write("sh: " .. tostring(why) .. "\n")
end
board.halt()

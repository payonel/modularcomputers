-- OpenOS's shell library for openos's programs (ported from OpenOS, MIT: see
-- /usr/share/openos/LICENSE: parse and resolve as OpenOS's), over mcos: the working directory is
-- the process's own (chdir, and PWD), a command runs with mcos's sh (shell.execute: what OpenOS's
-- shell ran, mcos's programs here), and aliases are the program's own table (mcos's sh keeps its
-- own). Made for a run, given its environment and its machine's command runner.
local sys = require("sys")
local unicode = require("openos.unicode")
local fs = require("openos.filesystem")

return function(environ, execute)
  local shell = {}
  local aliases = {}

  function shell.getAlias(alias)
    return aliases[alias]
  end

  function shell.setAlias(alias, value)
    checkArg(1, alias, "string")
    checkArg(2, value, "string", "nil")
    aliases[alias] = value
  end

  function shell.aliases()
    return pairs(aliases)
  end

  function shell.getWorkingDirectory()
    return environ.PWD or sys.getcwd() or "/"
  end

  function shell.setWorkingDirectory(dir)
    checkArg(1, dir, "string")
    dir = fs.canonical(dir):gsub("^$", "/"):gsub("(.)/$", "%1")
    if not fs.isDirectory(dir) or not sys.chdir(dir) then
      return nil, "not a directory"
    end
    environ.PWD = sys.getcwd()
    return true
  end

  function shell.getPath()
    return environ.PATH
  end

  function shell.setPath(value)
    environ.PATH = value
  end

  function shell.getShell()
    error("openos: OpenOS's shell is mcos's sh here: shell.execute runs a command with it", 2)
  end

  function shell.resolve(path, ext)
    checkArg(1, path, "string")
    local dir = path
    if dir:find("/") ~= 1 then
      dir = fs.concat(shell.getWorkingDirectory(), dir)
    end
    local name = fs.name(path)
    dir = fs[name and "path" or "canonical"](dir)
    local fullname = fs.concat(dir, name or "")
    if not ext then
      return fullname
    elseif name then
      checkArg(2, ext, "string")
      local search_in = path:find("/") and dir or environ.PATH or ""
      for search_path in string.gmatch(search_in, "[^:]+") do
        local search_name = fs.concat(shell.resolve(search_path), name)
        if not fs.exists(search_name) then
          search_name = search_name .. "." .. ext
        end
        if fs.exists(search_name) and not fs.isDirectory(search_name) then
          return search_name
        end
      end
    end
    return nil, "file not found"
  end

  function shell.parse(...)
    local params = table.pack(...)
    local args = {}
    local options = {}
    local doneWithOptions = false
    for i = 1, params.n do
      local param = params[i]
      if not doneWithOptions and type(param) == "string" then
        if param == "--" then
          doneWithOptions = true
        elseif param:sub(1, 2) == "--" then
          local key, value = param:match("%-%-(.-)=(.*)")
          if not key then
            key, value = param:sub(3), true
          end
          options[key] = value
        elseif param:sub(1, 1) == "-" and param ~= "-" then
          for j = 2, unicode.len(param) do
            options[unicode.sub(param, j, j)] = true
          end
        else
          table.insert(args, param)
        end
      else
        table.insert(args, param)
      end
    end
    return args, options
  end

  -- A command line, with mcos's sh: true, or false and why (its status).
  function shell.execute(command, env, ...)
    checkArg(1, command, "string")
    local words = {command}
    for _, arg in ipairs({...}) do
      words[#words + 1] = "'" .. tostring(arg):gsub("'", "'\\''") .. "'"
    end
    local ok, how, status = execute(table.concat(words, " "))
    if ok then
      return true
    end
    return false, how == "exit" and ("exit status " .. tostring(status)) or tostring(status)
  end

  return shell
end

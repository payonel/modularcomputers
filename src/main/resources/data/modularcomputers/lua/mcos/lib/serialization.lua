-- serialization: Lua values as text, and back. serialize(value) is Lua source that unserialize (or
-- load) makes the value again: nil, booleans, numbers (integers stay integers; inf and nan too),
-- strings, and tables of those, keys sorted; a function, a thread or a userdata, or a table met
-- again inside itself, can't be, and is an error. serialize(value, true) is for reading instead:
-- anything goes (a function as function: 0x...; a table met again as <cycle>), and a table that's
-- long goes on several lines, indented.
local serialization = {}

local LINE = 72 -- columns a table's text may take on one line before it's put on several
local INDENT = "  "

local KEYWORDS = {}
for word in string.gmatch("and break do else elseif end false for function goto if in local nil not or repeat return then true until while", "%a+") do
  KEYWORDS[word] = true
end

-- The order keys are written in: numbers first, then strings, then the rest, each sorted.
local KIND = {number = 1, string = 2, boolean = 3}
local function before(a, b)
  local ka, kb = KIND[type(a)] or 4, KIND[type(b)] or 4
  if ka ~= kb then
    return ka < kb
  elseif ka == 4 then
    return tostring(a) < tostring(b)
  elseif ka == 3 then
    return not a and b
  end
  return a < b
end

local function number(n)
  if n ~= n then
    return "0/0"
  elseif n == math.huge then
    return "math.huge"
  elseif n == -math.huge then
    return "-math.huge"
  elseif math.type(n) == "integer" then
    return string.format("%d", n)
  end
  local text = string.format("%.17g", n)
  if tonumber(text) ~= n then
    text = string.format("%a", n)
  elseif not string.find(text, "[%.eEn]") then
    text = text .. ".0" -- (a float stays a float)
  end
  return text
end

local function write(value, pretty, seen, depth)
  local kind = type(value)
  if kind == "nil" or kind == "boolean" then
    return tostring(value)
  elseif kind == "number" then
    return number(value)
  elseif kind == "string" then
    return string.format("%q", value)
  elseif kind ~= "table" then
    if pretty then
      return tostring(value)
    end
    error("cannot serialize a " .. kind, 0)
  end
  if seen[value] then
    if pretty then
      return "<cycle>"
    end
    error("cannot serialize a table that holds itself", 0)
  end
  seen[value] = true
  local keys = {}
  for key in pairs(value) do
    keys[#keys + 1] = key
  end
  table.sort(keys, before)
  local parts = {}
  local n = 0 -- (the sequence part is written without its keys)
  for _, key in ipairs(keys) do
    local text = write(value[key], pretty, seen, depth + 1)
    if key == n + 1 and math.type(key) == "integer" then
      n = key
      parts[#parts + 1] = text
    elseif type(key) == "string" and string.find(key, "^[%a_][%w_]*$") and not KEYWORDS[key] then
      parts[#parts + 1] = key .. " = " .. text
    else
      parts[#parts + 1] = "[" .. write(key, pretty, seen, depth + 1) .. "] = " .. text
    end
  end
  seen[value] = nil
  local flat = "{" .. table.concat(parts, ", ") .. "}"
  if not pretty or (#flat <= LINE and not string.find(flat, "\n", 1, true)) or #parts == 0 then
    return flat
  end
  local inner = string.rep(INDENT, depth + 1)
  return "{\n" .. inner .. table.concat(parts, ",\n" .. inner) .. "\n" .. string.rep(INDENT, depth) .. "}"
end

function serialization.serialize(value, pretty)
  return write(value, pretty, {}, 0)
end

-- The value a serialize gave (read with nothing but math in reach); nil and why if it isn't one.
function serialization.unserialize(text)
  local chunk, why = load("return " .. text, "=data", "t", {math = {huge = math.huge}})
  if not chunk then
    return nil, why
  end
  local ok, value = pcall(chunk)
  if not ok then
    return nil, value
  end
  return value
end

return serialization

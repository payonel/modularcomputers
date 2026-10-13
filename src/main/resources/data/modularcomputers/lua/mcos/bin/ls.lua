-- ls [path]: what's in a directory.
local path = mcos.resolve(... or ".")
local names, why = mcos.fs.list(path)
if not names then
  mcos.term.write("ls: " .. tostring(why) .. "\n")
  return
end
mcos.term.write(table.concat(names, "  ") .. (#names > 0 and "\n" or ""))

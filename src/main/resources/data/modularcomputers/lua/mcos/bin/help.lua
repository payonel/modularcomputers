-- help: the commands there are.
local names = {"cd", "exit"}
for _, name in ipairs(mcos.fs.list("/bin") or {}) do
  names[#names + 1] = name:gsub("%.lua$", "")
end
table.sort(names)
mcos.term.write(table.concat(names, "  ") .. "\n")

-- cat file...: files' contents.
for _, name in ipairs({...}) do
  local text, why = mcos.fs.read(mcos.resolve(name))
  if text then
    mcos.term.write(text)
  else
    mcos.term.write("cat: " .. name .. ": " .. tostring(why) .. "\n")
  end
end

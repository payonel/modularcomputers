-- words: a command line as words: spaces part them; quotes keep spaces in.
local words = {}

function words.split(line)
  local result, word, quote = {}, nil, nil
  for i = 1, #line do
    local c = line:sub(i, i)
    if quote then
      if c == quote then
        quote = nil
      else
        word = (word or "") .. c
      end
    elseif c == '"' or c == "'" then
      quote = c
      word = word or ""
    elseif c == " " or c == "\t" then
      if word then
        result[#result + 1] = word
        word = nil
      end
    else
      word = (word or "") .. c
    end
  end
  if word then
    result[#result + 1] = word
  end
  return result
end

return words

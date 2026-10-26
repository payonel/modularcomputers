-- hello, world: on the console, then waiting, the machine on (leaving its EEPROM is an abort)
board.invoke("console", "write", "hello world\r\n")
while true do
  board.waitForInterrupt()
end

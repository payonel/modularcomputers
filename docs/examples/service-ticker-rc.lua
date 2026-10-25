-- /etc/rc.d/ticker: the ticker service, a line every 2 seconds, its output and errors in /tmp.
description = "writes the time to /home/ticker.log"
command = "/bin/lua"
command_args = {"/home/ticker.lua", "2"}
output_log = "/tmp/ticker.out"

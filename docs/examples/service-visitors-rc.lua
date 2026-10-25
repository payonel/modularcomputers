-- /etc/rc.d/visitors: the visitors service's definition, for rc. Its settings are all it needs:
-- rc starts the command in the background, keeps its pid in /run/visitors.pid, and stops it.
description = "logs what the motion sensor sees"
command = "/bin/lua"
command_args = {"/home/visitors.lua"}
output_log = "/tmp/visitors.log"

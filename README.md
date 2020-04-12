# httpnio

To run the CLI programs, ensure you have Java 12+ and Gradle 6.0.1+ installed.

Build the project using `./gradlew build`.

The program can then be ran through the shell script `httpc` or `httpfs`, otherwise, the 
`./gradlew build` script should take care of producing a jar file in the `build/libs/httpsocketclient.jar`
(see the `httpc` and `httpfs` to see how the arguments are passed).

`./httpc help` and `./httpfs help` are the commands for showing the help messages.

Example usages are provided:
```shell
# Server
$ ./httpfs help

# Server (TCP mode)
$ ./httpfs -v -p 8007 -d '/path/to/directory'

# Server (UDP mode)
$ ./httpfs -v --udp -p 8007 -d '/path/to/directory' 

# Client
$ ./httpc help
$ ./httpc help post

$ Client (TCP mode)
$ ./httpc get -v -h 'Content-Type: application/json' -d '{ "Hey": "there" }' http://100.25.11.135/anything
$ ./httpc post -v -h 'Content-Type: application/json' -f "/path/to/body.txt" -o "/path/to/out.txt" http://httpbin.org/anything
$ ./httpc get -v --header Content-Type:application/json 'http://postman-echo.com/get?foo1=bar1&foo2=bar2'

$ Client (UDP mode)
$ ./httpc post --udp -v -p '/some/hello.txt' -d 'HEY THERE YOU!' 'localhost:8007'
$ ./httpc get --udp -v -p '/some/hello.txt' 'localhost:8007'
$ ./httpc get --udp -v -p '/build' 'localhost:8007'
```
**NOTE 1**: The format of the host url in UDP mode should not contain anything other than the host name/address with the 
port separated by a colon (e.g. `127.0.0.1:8007` or `localhost:8007`) and if needed a path specifier using the `-p` option 
(e.g. `-p /some/file.txt` or `-p /some/directory`.

**NOTE 2**: The router in UDP mode for the server must be running on localhost on port 3000.

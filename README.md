# httpsocketclient

To run the CLI program, ensure you have Java 12+ and Gradle 6.0.1+ installed.

Build the project using `./gradlew build`.

The program can then be ran through the shell script `httpc`.

Example usages are provided:
```shell
$ ./httpc help
$ ./httpc post help
$ ./httpc get -v -h 'Content-Type: application/json' -d '{ "Hey": "there" }' http://100.25.11.135/anything
$ ./httpc post -v -h 'Content-Type: application/json' -f "/path/to/body.txt" -o "/path/to/out.txt" http://httpbin.org/anything
$ ./httpc get -v --header Content-Type:application/json 'http://postman-echo.com/get?foo1=bar1&foo2=bar2'
```

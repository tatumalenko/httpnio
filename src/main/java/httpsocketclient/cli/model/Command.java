package httpsocketclient.cli.model;

import java.util.List;

public enum Command implements ValueResolvable, ArgumentResolvable {
    GET(new Help("get",
        "executes a HTTP GET request and prints the response.",
        "Get executes a HTTP GET request for a given URL.",
        null,
        List.of(Option.VERBOSE, Option.HEADER), Argument.URL)),
    POST(new Help("post",
        "executes a HTTP POST request and prints the response.",
        "Post executes a HTTP POST request for a given URL with inline data or from file.",
        "Either [-d] or [-f] can be used but not both.",
        List.of(Option.VERBOSE, Option.HEADER, Option.DATA, Option.FILE), Argument.URL)),
    HELP(new Help("help",
        "prints this screen.",
        "httpc is a curl-like application but supports HTTP protocol only.",
        "Use \"httpc help [command]\" for more information about a command.",
        null, Argument.COMMAND));

    private final String value;
    private final Help help;
    private final List<Option> options;
    private final Argument argument;

    Command(final Help help) {
        value = help.name();
        this.help = help;
        options = help.options();
        argument = help.argument();
    }

    @Override
    public String value() {
        return value;
    }

    public Help help() {
        return help;
    }

    public List<Option> options() {
        return options;
    }

    @Override
    public Argument argument() {
        return argument;
    }
}
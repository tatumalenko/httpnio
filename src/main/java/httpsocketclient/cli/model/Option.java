package httpsocketclient.cli.model;

public enum Option implements ValueResolvable, ArgumentResolvable {
    VERBOSE("-v", false, "-v", "Prints the detail of the response such as protocol, status, and headers.", Argument.NONE),
    HEADER("-h", true, "-h key:value", "Associates headers to HTTP Request with the format 'key:value'.", Argument.KEYVALUE),
    DATA("-d", true, "-d string", "Associates an inline data to the body HTTP POST request.", Argument.JSON),
    FILE("-f", true, "-f file", "Associates the content of a file to the body HTTP POST request.", Argument.FILE);

    final String value;
    final boolean hasArg;
    final String format;
    final String info;
    final Argument argument;

    Option(final String value, final boolean hasArg, final String format, final String info, final Argument argument) {
        this.value = value;
        this.hasArg = hasArg;
        this.format = format;
        this.info = info;
        this.argument = argument;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public Argument argument() {
        return argument;
    }
}
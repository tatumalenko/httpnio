package httpsocketclient.cli.model;

public enum Argument {
    URL("URL", "'?(http:\\/\\/[A-Za-z0-9\\-._~:/?#\\[\\]@!$&()*\\+,;=]*)'?"),
    KEYVALUE("key:value", "([\\w:\\-\\/]+)"),
    JSON("{\"prop\": \"value\"}", "(\\{[\\w:\\-/'\",\\s+]+\\})"),
    FILE("/some/file/location", "(\\/[\\w\\W]+\\.txt)"),
    NONE("None", "(\\s*)"),
    COMMAND("Command", "(get|post|help)");

    private final String value;
    private final String regex;

    Argument(final String value, final String regex) {
        this.value = value;
        this.regex = regex;
    }

    public String value() {
        return value;
    }

    public String regex() {
        return regex;
    }

    public boolean isValid(final String text) {
        return text.matches(regex);
    }
}
package httpsocketclient.cli.parse;

import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface Parser {

    static <T> T make(final Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (final InstantiationException e) {
            e.printStackTrace();
        } catch (final IllegalAccessException e) {
            e.printStackTrace();
        } catch (final NoSuchMethodException e) {
            e.printStackTrace();
        } catch (final InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    static <T> T parse(final Class<T> clazz, final String in) {

        final T tokenized = tokenize(clazz, in);


        return tokenized;
    }

    @Data
    @AllArgsConstructor
    class Tokenized {
        private CLI.Command command;
        private CLI.SubCommand subCommand;
        private List<CLI.Flag> flags;
        private List<CLI.Option> options;
        private Map<CLI.Argument, String> arguments;

        @Override
        public String toString() {
            return "Command: " + command.name()
                + "\nsubCommand: " + subCommand.name()
                + "\nsubCommand.argument: " + subCommand.argument().name()
                + "\nflags: " + flags.stream().map(e -> e.name()).collect(Collectors.joining(", "))
                + "\noptions: " + options.stream().map(e -> e.name()).collect(Collectors.joining(", "))
                + "\narguments: " + arguments.entrySet().stream().map(entry -> entry.getKey().name() + ": " + entry.getValue()).collect(Collectors.joining("; "));
        }
    }

    static <T> T tokenize(final Class<T> clazz, final String in) {
        try {
            final T instance = make(clazz);

            final CLI.Command command;
            final CLI.SubCommand subCommand;
            CLI.Argument argument = null;
            final List<CLI.Flag> flags = new ArrayList<>();
            final List<CLI.Option> options = new ArrayList<>();
            final Map<CLI.Argument, String> arguments = new HashMap<>();

            final var parser = new Checker(in);
            final var lexemes = splitBySpaceOrSingleQuote(in);

            command = parser.command(lexemes.get(0));
            if (command == null) {
                throw new RuntimeException(lexemes.get(0) + " is not a valid command!");
            }
            lexemes.remove(0);

            if (lexemes.get(0).equals("help")) {
                System.out.println(parser.help());
                System.exit(0);
            }

            subCommand = parser.subCommand(lexemes.get(0));
            if (subCommand == null) {
                throw new RuntimeException(lexemes.get(0) + " is not a valid subcommand!");
            }
            lexemes.remove(0);

            for (int i = 0; i < lexemes.size(); i++) {
                final var lexeme = lexemes.get(i);
                final var nextLexeme = i <= lexemes.size() - 2 ? lexemes.get(i + 1) : null;

                if (lexeme.equals("help")) {
                    System.out.println(parser.help(subCommand));
                    System.exit(0);
                }

                if (parser.isFlag(lexeme)) {
                    flags.add(parser.flag(lexeme));
                    setField(instance, clazz, parser.flag(lexeme).name(), true);
                } else if (parser.isOption(lexeme)) {
                    final var option = parser.option(lexeme);
                    if (nextLexeme == null) {
                        throw new RuntimeException("No valid argument was found following the " + option.name() + " option!");
                    }

                    final var arg = parser.argument(option, nextLexeme);
                    if (arg == null) {
                        throw new RuntimeException(nextLexeme + " is not a valid argument for option " + option.name() + "!");
                    }

                    options.add(option);
                    arguments.put(arg, nextLexeme);

                    if (clazz.getDeclaredField(option.name()).getType() == String.class) {
                        setField(instance, clazz, option.name(), nextLexeme);
                    } else if (clazz.getDeclaredField(option.name()).getType() == List.class) {
                        List<String> value = (List<String>) getField(instance, clazz, option.name());
                        value = value == null ? new ArrayList<>() : value;
                        value.add(nextLexeme);
                        setField(instance, clazz, option.name(), value);
                    } else {
                        System.out.println("Warning: " + option.name() + " was neither a String or List<String>, and hence could not be set.");
                    }


                    i++;
                } else if (parser.isArgument(subCommand, lexeme)) {
                    if (argument != null) {
                        throw new RuntimeException("More than one argument for sub-command " + subCommand.name() + " was found!");
                    }
                    argument = parser.argument(subCommand, lexeme);
                    if (argument == null) {
                        throw new RuntimeException(lexeme + " is not a valid subcommand argument for + " + subCommand.name() + "!");
                    }

                    arguments.put(argument, lexeme);

                    setField(instance, clazz, subCommand.name(), lexeme);
                } else {
                    throw new RuntimeException(lexeme + " is not a valid flag or option!");
                }
            }

//            return new Tokenized(command, subCommand, flags, options, arguments);
            return instance;
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    static <T> void setField(final T instance, final Class<T> clazz, final String name, final Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    static <T> Object getField(final T instance, final Class<T> clazz, final String name) throws IllegalAccessException, NoSuchFieldException {
        final Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    class Checker {
        CLI.Command command;

        List<CLI.SubCommand> subCommands;

        List<CLI.Flag> flags;

        List<CLI.Option> options;

        public Checker(final String in) {
            final List<Class<?>> cmdClasses = Thread.getAllStackTraces()
                .values()
                .stream()
                .flatMap(Arrays::stream)
                .map(e -> Try.of(() -> Class.forName(e.getClassName())).getOrNull())
                .filter(e -> !Arrays.asList(e.getAnnotationsByType(CLI.Command.class)).isEmpty())
                .collect(Collectors.toList());

            final String cmdIn = in.split("\\s+")[0];
            final List<CLI.Command> commands = cmdClasses.stream()
                .map(e -> {
                    var cmdClass = e.getAnnotationsByType(CLI.Command.class);
                    return cmdClass.length > 0 ? cmdClass[0] : null;
                })
                .filter(e -> e != null)
                .collect(Collectors.toList());

            final Class<?> cmdClass = cmdClasses.stream()
                .filter(e -> Arrays.asList(e.getAnnotationsByType(CLI.Command.class)[0].name()).contains(cmdIn))
                .findFirst()
                .orElse(null);

            command = commands.stream()
                .filter(e -> Arrays.asList(cmdClass.getAnnotationsByType(CLI.Command.class)[0].name()).contains(cmdIn))
                .findFirst()
                .orElse(null);

            subCommands = annotationsWithType(cmdClass, CLI.SubCommand.class);
            flags = annotationsWithType(cmdClass, CLI.Flag.class);
            options = annotationsWithType(cmdClass, CLI.Option.class);

        }

        boolean isCommand(final String in) {
            return command(in) != null;
        }

        CLI.Command command(final String in) {
            return command;
        }

        boolean isSubCommand(final String in) {
            return subCommand(in) != null;
        }

        CLI.SubCommand subCommand(final String in) {
            return subCommands.stream().filter(e -> Arrays.asList(e.name()).contains(in)).findFirst().orElse(null);
        }

        boolean isFlag(final String in) {
            return flag(in) != null;
        }

        CLI.Flag flag(final String in) {
            return flags.stream().filter(e -> Arrays.asList(e.alias()).contains(in)).findFirst().orElse(null);
        }

        boolean isOption(final String in) {
            return option(in) != null;
        }

        CLI.Option option(final String in) {
            return options.stream().filter(e -> Arrays.asList(e.alias()).contains(in)).findFirst().orElse(null);
        }

        boolean isArgument(final CLI.Option option, final String in) {
            return argument(option, in) != null;
        }

        boolean isArgument(final CLI.SubCommand subCommand, final String in) {
            return argument(subCommand, in) != null;
        }

        CLI.Argument argument(final CLI.Option option, final String in) {
            return in.matches(option.argument().regex()) ? option.argument() : null;
        }

        CLI.Argument argument(final CLI.SubCommand subCommand, final String in) {
            return in.matches(subCommand.argument().regex()) ? subCommand.argument() : null;
        }

        String help() {
            var sb = command.name() + ": " + command.description() + "\n";

            sb += "\nUsage:\n";
            sb += "   " + command.name() + " <subcommand> [options]\n";
            sb += "The subcommands are:\n";
            for (final var subCommand : subCommands) {
                sb += "   " + subCommand.name() + ":\t" + subCommand.description() + "\n";
            }
            sb += "   help:\tprints this output.\n";
            sb += "Use \"" + command.name() + " help <subcommand>\" for more information about a subcommand.";

            return sb;
        }

        String help(final CLI.SubCommand subCommand) {
            var sb = "\nSubcommand (" + subCommand.name() + "): " + subCommand.description();

            sb += "\nUsage:\n   " + command.name() + " " + subCommand.name() + " [options] " + subCommand.argument().name();
            sb += "\nOptions:\n";

            return sb;
        }
    }

    static List<String> splitBySpaceOrSingleQuote(final String in) {
        return Pattern
            .compile("([^']\\S*|'.+?')\\s*")
            .matcher(in)
            .results()
            .map(MatchResult::group)
            .map(String::trim)
            .collect(Collectors.toList());
    }

    static <T extends Annotation> List<T> annotationsWithType(final Class<?> cls, final Class<T> annotation) {
        return Arrays.stream(cls.getDeclaredFields())
            .flatMap(e -> Arrays.stream(e.getAnnotationsByType(annotation)))
            .collect(Collectors.toList());
    }
}

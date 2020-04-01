package httpnio.cli;

import io.vavr.control.Either;
import io.vavr.control.Try;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Parser<T> {
    Class<T> clazz;
    Checker<T> checker;

    public Parser(final Class<T> clazz) {
        this.clazz = clazz;
        checker = new Checker<>(clazz);
    }

    public Try<Either<String, T>> parse(final String in) {
        return Try.of(() -> tokenize(in));
    }

    private Either<String, T> tokenize(final String in) throws ParseError {
        try {
            final T instance = make(clazz);

            final Command command;
            SubCommand subCommand = null;
            Argument argument = null;

            var lexemes = splitBySpace(in);

            command = checker.command(lexemes.get(0));
            if (command == null) {
                throw new ParseError(lexemes.get(0) + " is not a valid command!");
            }
            lexemes.remove(0);

            if (lexemes.get(0).equals("help")) {
                return Either.left(checker.help());
            }

            if (!checker.subCommands.isEmpty()) {
                subCommand = checker.subCommand(lexemes.get(0));
                if (subCommand == null) {
                    throw new ParseError(lexemes.get(0) + " is not a valid subCommand!");
                }
                lexemes.remove(0);

                if (lexemes.get(0).equals("help")) {
                    return Either.left(checker.help(subCommand));
                }

                final var lastLexeme = lexemes.get(lexemes.size() - 1);
                if (checker.isArgument(subCommand, lastLexeme)) {
                    argument = checker.argument(subCommand, lastLexeme);
                    if (argument == null) {
                        throw new ParseError(lastLexeme + " is not a valid subCommand argument for + " + subCommand.name() + "!");
                    }

                    setField(instance, clazz, subCommand.name(), lastLexeme);
                }
                lexemes.remove(lexemes.get(lexemes.size() - 1));
            }

            lexemes = splitByFlagOrOption(String.join(" ", lexemes));

            for (int i = 0; i < lexemes.size(); i++) {
                final var lexeme = lexemes.get(i);
                final var nextLexeme = i <= lexemes.size() - 2 ? lexemes.get(i + 1) : null;

                if (checker.isFlag(lexeme)) {
                    setField(instance, clazz, checker.flag(lexeme).name(), true);
                } else if (checker.isOption(lexeme)) {
                    final var option = checker.option(lexeme);
                    if (nextLexeme == null) {
                        throw new ParseError("No valid argument was found following the " + option.name() + " option!");
                    }

                    final var arg = checker.argument(option, nextLexeme);
                    if (arg == null) {
                        throw new ParseError(nextLexeme + " is not a valid argument for option " + option.name() + "!");
                    }

                    if (clazz.getDeclaredField(option.name()).getType() == String.class) {
                        setField(instance, clazz, option.name(), nextLexeme);
                    } else if (clazz.getDeclaredField(option.name()).getType() == List.class) {
                        @SuppressWarnings("unchecked")
                        List<String> value = (List<String>) getField(instance, clazz, option.name());
                        value = value == null ? new ArrayList<>() : value;
                        value.add(nextLexeme);
                        setField(instance, clazz, option.name(), value);
                    } else if (clazz.getDeclaredField(option.name()).getType().toString().equals("int")) {
                        setField(instance, clazz, option.name(), Integer.parseInt(nextLexeme));
                    } else {
                        System.out.println("Warning: " + option.name() + " was neither a String or List<String>, and hence could not be set.");
                    }
                    i++;
                } else if (checker.isArgument(subCommand, lexeme)) {
                    if (argument != null) {
                        throw new ParseError("More than one argument for subCommand " + subCommand.name() + " was found!");
                    }
                    argument = checker.argument(subCommand, lexeme);
                    if (argument == null) {
                        throw new ParseError(lexeme + " is not a valid subCommand argument for + " + subCommand.name() + "!");
                    }

                    setField(instance, clazz, subCommand.name(), lexeme);
                } else if (checker.isSubCommand(lexeme)) {
                    throw new ParseError("Second subCommand specified: " + lexeme + ". SubCommand '" + subCommand.name() + "' was already supplied.");
                } else {
                    throw new ParseError(lexeme + " is not a valid flag or option!");
                }
            }

            return Either.right(instance);
        } catch (final Exception e) {
            throw new ParseError(e.getMessage(), e);
        }
    }

    public String help() {
        return checker.help();
    }

    public String help(final SubCommand subCommand) {
        return checker.help(subCommand);
    }

    private static <T> T make(final Class<T> clazz) throws ParseError {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (final InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new ParseError("The class " + clazz.getSimpleName() + " could not be instantiated for some reason. Please make sure that the annotations were used properly.");
        }
    }

    private static <T> void setField(
        final T instance,
        final Class<T> clazz,
        final String name,
        final Object value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static <T> Object getField(
        final T instance,
        final Class<T> clazz,
        final String name) throws IllegalAccessException, NoSuchFieldException {
        final Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    static class Checker<T> {
        Command command;

        List<SubCommand> subCommands;

        List<Flag> flags;

        List<Option> options;

        public Checker(final Class<T> cmdClass) {
            command = cmdClass.getAnnotationsByType(Command.class)[0];
            subCommands = annotationsWithType(cmdClass, SubCommand.class);
            flags = annotationsWithType(cmdClass, Flag.class);
            options = annotationsWithType(cmdClass, Option.class);
        }

        boolean isCommand(final String in) {
            return command(in) != null;
        }

        Command command(final String in) {
            return command;
        }

        boolean isSubCommand(final String in) {
            return subCommand(in) != null;
        }

        SubCommand subCommand(final String in) {
            return subCommands.stream().filter(e -> Arrays.asList(e.name()).contains(in)).findFirst().orElse(null);
        }

        boolean isFlag(final String in) {
            return flag(in) != null;
        }

        Flag flag(final String in) {
            return flags.stream().filter(e -> Arrays.asList(e.alias()).contains(in)).findFirst().orElse(null);
        }

        boolean isOption(final String in) {
            return option(in) != null;
        }

        Option option(final String in) {
            return options.stream().filter(e -> Arrays.asList(e.alias()).contains(in)).findFirst().orElse(null);
        }

        boolean isArgument(final Option option, final String in) {
            return argument(option, in) != null;
        }

        boolean isArgument(final SubCommand subCommand, final String in) {
            return argument(subCommand, in) != null;
        }

        Argument argument(final Option option, final String in) {
            return in.matches(option.argument().regex()) ? option.argument() : null;
        }

        Argument argument(final SubCommand subCommand, final String in) {
            if (subCommand == null) {
                return null;
            }
            return in.matches(subCommand.argument().regex()) ? subCommand.argument() : null;
        }

        String help() {
            final String NAME_DESCRIPTION_TEMPLATE = "   %-20s%s%n";
            final var sb = new StringBuilder();
            sb.append(String.format("%n"));
            sb.append(String.format("%s: %s%n", command.name(), command.description()));
            sb.append(String.format(
                "%nUsage:%n   %s" + (subCommands.isEmpty() ? "" : " <subCommand>") + " [flags] [options]%n",
                command.name()));

            if (!subCommands.isEmpty()) {
                sb.append(String.format("\nThe subCommands are:%n"));
            }
            for (final var subCommand : subCommands) {
                sb.append(String.format(NAME_DESCRIPTION_TEMPLATE, subCommand.name(), subCommand.description()));
            }

            if (!flags.isEmpty()) {
                if (flags.stream().anyMatch(e -> e.subCommands().length == 0)) {
                    sb.append(String.format("\nThe flags are:%n"));
                }
                for (final var flag : flags) {
                    if (flag.subCommands().length == 0) {
                        sb.append(String.format(NAME_DESCRIPTION_TEMPLATE, flag.name(), flag.description()));
                    }
                }
            }

            if (!options.isEmpty()) {
                if (options.stream().anyMatch(e -> e.subCommands().length == 0)) {
                    sb.append(String.format("\nThe options are:%n"));
                }
                for (final var option : options) {
                    if (option.subCommands().length == 0) {
                        sb.append(String.format(NAME_DESCRIPTION_TEMPLATE, option.name(), option.description()));
                    }
                }
            }

            sb.append(String.format("   %-20sPrints this output.%n", "help"));

            if (subCommands.stream().filter(e -> !e.name().equalsIgnoreCase("help")).count() > 0) {
                sb.append(String.format("\nUse \"%s help <subCommand>\" for more information about a subCommand", command.name()));
            }

            return sb.toString();
        }

        String help(final SubCommand subCommand) {
            final List<Option> validOptions = options.stream()
                .filter(e -> Arrays.asList(e.subCommands()).contains(subCommand.name()))
                .collect(Collectors.toList());
            final List<Flag> validFlags = flags.stream()
                .filter(e -> Arrays.asList(e.subCommands()).contains(subCommand.name()))
                .collect(Collectors.toList());

            final var sb = new StringBuilder();
            sb.append(String.format("%n"));
            sb.append(String.format("%nSubCommand (%s): %s", subCommand.name(), subCommand.description()));
            sb.append(String.format(
                "%nUsage:%n   %s %s [flags] [options] %s",
                command.name(),
                subCommand.name(),
                subCommand.argument().name()));
            sb.append(String.format("%nFlags:"));
            for (final var validFlag : validFlags) {
                sb.append(String.format("%n   %-30s%s", validFlag.alias()[0] + " [" + validFlag.alias()[1] + "]", validFlag.description()));
            }
            sb.append(String.format("%nOptions:"));
            for (final var validOption : validOptions) {
                sb.append(String.format(
                    "%n   %-30s%s",
                    validOption.alias()[0] + " [" + validOption.alias()[1] + "] " + validOption.argument().format(),
                    validOption.description()));
            }

            return sb.toString();
        }
    }

    private static List<String> splitBySpace(final String in) {
        return Arrays.stream(in.split(" ")).filter(e -> !e.trim().equals("")).collect(Collectors.toList());
    }

    private List<String> splitByFlagOrOption(final String in) {
        final var command = checker.command.name();
        final var subCommands = checker.subCommands.stream().map(SubCommand::name).collect(Collectors.toList());
        final var flags = checker.flags.stream().flatMap(e -> Arrays.stream(e.alias())).collect(Collectors.toList());
        final var options = checker.options.stream().flatMap(e -> Arrays.stream(e.alias())).collect(Collectors.toList());
        final var tokens = new ArrayList<>();
        tokens.add(command);
        tokens.addAll(subCommands);
        tokens.addAll(flags);
        tokens.addAll(options);
        final List<String> args = new ArrayList<>();

        var lastWasToken = false;
        for (final var arg : splitBySpace(in)) {
            final var idx = args.size() - 1;

            final var isToken = tokens.contains(arg);
            if (isToken) {
                args.add(arg);
            } else if (!lastWasToken) {
                if (idx > -1) {
                    args.set(idx, args.get(idx) + " " + arg);
                } else {
                    args.add(arg);
                }
            } else {
                args.add(arg);
            }

            lastWasToken = isToken;
        }
        return args;
    }

    private static <T extends Annotation> List<T> annotationsWithType(final Class<?> cls, final Class<T> annotation) {
        return Arrays.stream(cls.getDeclaredFields())
            .flatMap(e -> Arrays.stream(e.getAnnotationsByType(annotation)))
            .collect(Collectors.toList());
    }
}

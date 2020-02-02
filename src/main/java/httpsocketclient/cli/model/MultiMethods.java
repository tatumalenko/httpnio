package httpsocketclient.cli.model;

import java.util.function.Function;
import java.util.stream.Stream;

public interface MultiMethods {

    interface Shape {
        default boolean is(final Class<? extends Shape> shape) {
            return shape.equals(getClass());
        }
    }

    interface Circle extends Shape {
    }

    interface Triangle extends Shape {
    }

    interface Rectangle extends Shape {
    }

    enum Shapes {Circle, Rectangle, Triangle}

    static int area(final Shape shape) {
        match(shape,
            (Circle c) -> 1,
            (Triangle t) -> 2,
            (Rectangle r) -> 3
        );

//        var r = switch() {
//            case Circle.class -> 1;
//            case Shape.class -> 2;
//        };

        return 1;
    }

    static <E extends T, T, R> void match(final T type, final Function<? extends E, R>... cases) {
//        type instanceof (cases[0].getClass().getTypeParameters())
//        var caseTypes = Arrays.stream(cases).map(e -> e.getClass().)
//        return new R();
        final var that = type.getClass().getInterfaces()[0];
        final var base = type.getClass().getInterfaces()[0].getInterfaces()[0];

        var pack = Stream.of(Package.getPackages()).filter(e -> e.equals(type.getClass().getInterfaces()[0].getPackage())).findFirst();

    }

    static void main(final String[] args) {
        final Circle p = new Circle() {
        };
        System.out.println(area(p));
//        System.out.println(area(Triangle.class));
//        System.out.println(area(Rectangle.class));
//        System.out.println(area(Shape.class));
    }
}

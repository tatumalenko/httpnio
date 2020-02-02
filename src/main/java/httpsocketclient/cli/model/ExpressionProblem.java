package httpsocketclient.cli.model;

public class ExpressionProblem {
    interface Expression {
    }

    interface Literal<T> extends Expression {
        T literal(Integer literal);
    }

    interface Add<T> extends Expression {
        T add(T a, T b);
    }

    interface Neg<T> extends Expression {
        T negate(T a);
    }

    interface Mult<T> extends Expression {
        T mult(T a, T b);
    }

    interface Operation<T> extends Literal<T>, Add<T>, Neg<T>, Mult<T> {
    }

    static class Eval implements Operation<Integer> {
        @Override
        public Integer literal(final Integer literal) {
            return literal;
        }

        @Override
        public Integer add(final Integer a, final Integer b) {
            return a + b;
        }

        @Override
        public Integer negate(final Integer a) {
            return -a;
        }

        @Override
        public Integer mult(final Integer a, final Integer b) {
            return a * b;
        }
    }

    static class Print implements Operation<String> {
        @Override
        public String literal(final Integer literal) {
            return String.valueOf(literal);
        }

        @Override
        public String add(final String a, final String b) {
            return "(add " + a + " " + b + ")";
        }

        @Override
        public String negate(final String a) {
            return "(negate " + a + ")";
        }

        @Override
        public String mult(final String a, final String b) {
            return "(mult " + a + " " + b + ")";
        }
    }

    static <E, T extends Operation<E>> E expression(final T op) {
        return op.mult(op.add(op.literal(9), op.negate(op.literal(4))), op.mult(op.literal(2), op.add(op.literal(1), op.literal(0))));
    }

    public static void main(final String[] args) {
        final Operation<String> print = new Print();
        System.out.println(expression(print));
        System.out.println(expression(new Eval()));
    }
}

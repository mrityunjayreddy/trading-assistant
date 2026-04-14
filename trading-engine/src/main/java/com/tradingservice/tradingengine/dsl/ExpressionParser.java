package com.tradingservice.tradingengine.dsl;

import com.tradingservice.tradingengine.dto.LogicalOperator;
import com.tradingservice.tradingengine.dto.RuleComparator;
import com.tradingservice.tradingengine.dto.RuleDefinition;
import com.tradingservice.tradingengine.exception.InvalidStrategyConfigurationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser that converts a string boolean expression into a
 * {@link RuleDefinition} tree understood by the engine's existing {@code RuleFactory}.
 *
 * <h3>Grammar</h3>
 * <pre>
 * expression := andExpr ( OR andExpr )*
 * andExpr    := primary ( AND primary )*
 * primary    := '(' expression ')' | comparison
 * comparison := operand comparator operand
 * operand    := IDENTIFIER | NUMBER
 * comparator := &lt; | &gt; | &lt;= | &gt;= | == | != | cross_above | cross_below | ca | cb
 * </pre>
 *
 * <h3>Operator mapping</h3>
 * <ul>
 *   <li>{@code <}, {@code <=}  → LESS_THAN</li>
 *   <li>{@code >}, {@code >=}  → GREATER_THAN</li>
 *   <li>{@code cross_above}, {@code ca} → CROSS_ABOVE</li>
 *   <li>{@code cross_below}, {@code cb} → CROSS_BELOW</li>
 *   <li>{@code ==}, {@code !=} → throws (not supported by TA4J rule set)</li>
 * </ul>
 */
public final class ExpressionParser {

    // ----- token constants (keywords are stored uppercase for comparison) -----
    private static final String AND          = "AND";
    private static final String OR           = "OR";
    private static final String LPAREN       = "(";
    private static final String RPAREN       = ")";
    private static final String COMMA        = ",";
    private static final String CROSS_ABOVE  = "CROSS_ABOVE";
    private static final String CROSS_BELOW  = "CROSS_BELOW";

    private final List<String> tokens;
    private int pos;

    private ExpressionParser(String expression) {
        this.tokens = tokenize(expression);
        this.pos = 0;
    }

    /** Entry point — parse the expression and return the root {@link RuleDefinition}. */
    public static RuleDefinition parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new InvalidStrategyConfigurationException("Strategy DSL expression must not be blank");
        }
        ExpressionParser parser = new ExpressionParser(expression.trim());
        RuleDefinition result = parser.parseExpression();
        if (parser.pos != parser.tokens.size()) {
            throw new InvalidStrategyConfigurationException(
                "Unexpected token '" + parser.tokens.get(parser.pos) + "' at position " + parser.pos
                    + " in expression: " + expression
            );
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Grammar rules
    // -------------------------------------------------------------------------

    /** expression := andExpr ( OR andExpr )* */
    private RuleDefinition parseExpression() {
        RuleDefinition left = parseAndExpr();
        while (pos < tokens.size() && tokens.get(pos).equalsIgnoreCase(OR)) {
            pos++;
            RuleDefinition right = parseAndExpr();
            left = composite(LogicalOperator.OR, left, right);
        }
        return left;
    }

    /** andExpr := primary ( AND primary )* */
    private RuleDefinition parseAndExpr() {
        RuleDefinition left = parsePrimary();
        while (pos < tokens.size() && tokens.get(pos).equalsIgnoreCase(AND)) {
            pos++;
            RuleDefinition right = parsePrimary();
            left = composite(LogicalOperator.AND, left, right);
        }
        return left;
    }

    /** primary := '(' expression ')' | comparison */
    private RuleDefinition parsePrimary() {
        if (pos < tokens.size() && tokens.get(pos).equals(LPAREN)) {
            pos++; // consume '('
            RuleDefinition inner = parseExpression();
            expect(RPAREN);
            return inner;
        }
        return parseComparison();
    }

    /**
     * comparison := operand comparator operand          (infix)
     *             | funcComparator '(' operand ',' operand ')'  (function-call)
     *
     * Both forms are equivalent: {@code cross_above(sma50, sma200)} and
     * {@code sma50 cross_above sma200} produce the same {@link RuleDefinition}.
     */
    private RuleDefinition parseComparison() {
        // Function-call form: cross_above(left, right) / cross_below(left, right)
        if (pos < tokens.size() && isFunctionCallOperator(tokens.get(pos))
                && pos + 1 < tokens.size() && tokens.get(pos + 1).equals(LPAREN)) {
            String opToken = tokens.get(pos++); // consume operator keyword
            pos++;                               // consume '('
            String left  = consumeOperand("left operand");
            expect(COMMA);
            String right = consumeOperand("right operand");
            expect(RPAREN);
            return buildComparison(left, opToken, right);
        }

        // Infix form: left cross_above right  /  left < right  etc.
        String left    = consumeOperand("left operand");
        String opToken = consumeOperator();
        String right   = consumeOperand("right operand");
        return buildComparison(left, opToken, right);
    }

    private RuleDefinition buildComparison(String left, String opToken, String right) {
        RuleComparator comparator = mapComparator(opToken);
        RuleDefinition.RuleDefinitionBuilder builder = RuleDefinition.builder()
            .left(left)
            .operator(comparator);
        try {
            builder.rightValue(Double.parseDouble(right));
        } catch (NumberFormatException e) {
            builder.rightIndicator(right);
        }
        return builder.build();
    }

    /** True for operator keywords that may appear in function-call position. */
    private boolean isFunctionCallOperator(String token) {
        return switch (token.toLowerCase()) {
            case "cross_above", "ca", "cross_below", "cb" -> true;
            default -> false;
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RuleDefinition composite(LogicalOperator op, RuleDefinition left, RuleDefinition right) {
        // Flatten: if left is already a composite with the same logical operator, extend it
        if (left.getLogicalOperator() == op && left.getRules() != null && !left.getRules().isEmpty()) {
            List<RuleDefinition> rules = new ArrayList<>(left.getRules());
            rules.add(right);
            return RuleDefinition.builder().logicalOperator(op).rules(rules).build();
        }
        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(left);
        rules.add(right);
        return RuleDefinition.builder().logicalOperator(op).rules(rules).build();
    }

    private String consumeOperand(String description) {
        if (pos >= tokens.size()) {
            throw new InvalidStrategyConfigurationException("Expected " + description + " but reached end of expression");
        }
        String token = tokens.get(pos);
        // Operand: identifier or number, not a keyword/operator/delimiter
        if (token.equalsIgnoreCase(AND) || token.equalsIgnoreCase(OR)
            || token.equals(LPAREN) || token.equals(RPAREN) || token.equals(COMMA)
            || isOperatorToken(token)) {
            throw new InvalidStrategyConfigurationException("Expected " + description + " but found: '" + token + "'");
        }
        pos++;
        return token;
    }

    private String consumeOperator() {
        if (pos >= tokens.size()) {
            throw new InvalidStrategyConfigurationException("Expected comparison operator but reached end of expression");
        }
        String token = tokens.get(pos);
        if (!isOperatorToken(token)) {
            throw new InvalidStrategyConfigurationException("Expected comparison operator but found: '" + token + "'");
        }
        pos++;
        return token;
    }

    private boolean isOperatorToken(String token) {
        return switch (token.toLowerCase()) {
            case "<", ">", "<=", ">=", "==", "!=" -> true;
            case "cross_above", "ca", "cross_below", "cb" -> true;
            default -> false;
        };
    }

    private RuleComparator mapComparator(String op) {
        return switch (op.toLowerCase()) {
            case "<", "<=" -> RuleComparator.LESS_THAN;
            case ">", ">=" -> RuleComparator.GREATER_THAN;
            case "cross_above", "ca" -> RuleComparator.CROSS_ABOVE;
            case "cross_below", "cb" -> RuleComparator.CROSS_BELOW;
            case "==", "!=" -> throw new InvalidStrategyConfigurationException(
                "Operator '" + op + "' is not supported — use < > <= >= cross_above cross_below"
            );
            default -> throw new InvalidStrategyConfigurationException("Unknown operator: " + op);
        };
    }

    private void expect(String expected) {
        if (pos >= tokens.size() || !tokens.get(pos).equals(expected)) {
            String found = pos < tokens.size() ? tokens.get(pos) : "end of expression";
            throw new InvalidStrategyConfigurationException(
                "Expected '" + expected + "' but found '" + found + "'"
            );
        }
        pos++;
    }

    // -------------------------------------------------------------------------
    // Tokenizer
    // -------------------------------------------------------------------------

    /**
     * Splits the expression into tokens:
     * identifiers (including dots), numbers, operators, parentheses.
     */
    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int len = expr.length();
        while (i < len) {
            char c = expr.charAt(i);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Parentheses and comma
            if (c == '(' || c == ')' || c == ',') {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }

            // Two-char operators: <=, >=, ==, !=
            if ((c == '<' || c == '>' || c == '!' || c == '=')
                    && i + 1 < len && expr.charAt(i + 1) == '=') {
                tokens.add(expr.substring(i, i + 2));
                i += 2;
                continue;
            }

            // Single-char operators: <, >
            if (c == '<' || c == '>') {
                tokens.add(String.valueOf(c));
                i++;
                continue;
            }

            // Numbers: digits (possibly with a leading dot or decimal point)
            if (Character.isDigit(c) || (c == '-' && i + 1 < len && Character.isDigit(expr.charAt(i + 1)))) {
                int start = i;
                if (c == '-') i++;
                while (i < len && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) i++;
                tokens.add(expr.substring(start, i));
                continue;
            }

            // Identifiers / keywords: letters, digits, underscores, dots (for bb.lower etc.)
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < len && (Character.isLetterOrDigit(expr.charAt(i))
                        || expr.charAt(i) == '_' || expr.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(expr.substring(start, i));
                continue;
            }

            throw new InvalidStrategyConfigurationException(
                "Unexpected character '" + c + "' at position " + i + " in expression: " + expr
            );
        }
        return tokens;
    }
}
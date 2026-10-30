package ma.enset.bank.transaction.web.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphQlConfig {

    private static final Coercing<BigDecimal, BigDecimal> BIG_DECIMAL_COERCING = new Coercing<>() {

        @Override
        public BigDecimal serialize(Object input, GraphQLContext context, Locale locale) {
            if (input instanceof BigDecimal value) {
                return value;
            }
            throw new CoercingSerializeException("Expected a BigDecimal but was " + input);
        }

        @Override
        public BigDecimal parseValue(Object input, GraphQLContext context, Locale locale) {
            try {
                return switch (input) {
                    case BigDecimal value -> value;
                    case String value -> new BigDecimal(value);
                    case Number value -> new BigDecimal(value.toString());
                    default -> throw new CoercingParseValueException("Invalid BigDecimal: " + input);
                };
            } catch (NumberFormatException e) {
                throw new CoercingParseValueException("Invalid BigDecimal: " + input, e);
            }
        }

        @Override
        public BigDecimal parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
            return switch (input) {
                case IntValue value -> new BigDecimal(value.getValue());
                case FloatValue value -> value.getValue();
                case StringValue value -> parseValue(value.getValue(), context, locale);
                default -> throw new CoercingParseLiteralException("Invalid BigDecimal literal");
            };
        }
    };

    @Bean
    RuntimeWiringConfigurer bigDecimalScalar() {
        GraphQLScalarType scalar = GraphQLScalarType.newScalar()
                .name("BigDecimal")
                .description("Decimal number with arbitrary precision, used for monetary amounts")
                .coercing(BIG_DECIMAL_COERCING)
                .build();
        return wiring -> wiring.scalar(scalar);
    }
}
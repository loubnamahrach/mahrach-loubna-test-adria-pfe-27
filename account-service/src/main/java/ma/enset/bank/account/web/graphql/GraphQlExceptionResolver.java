package ma.enset.bank.account.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.enset.bank.account.service.exception.AccountBlockedException;
import ma.enset.bank.account.service.exception.AccountNotFoundException;
import ma.enset.bank.account.service.exception.InsufficientFundsException;
import ma.enset.bank.account.service.exception.InvalidOperationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Traduit les exceptions metier en erreurs GraphQL classifiees.
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType type = switch (ex) {
            case AccountNotFoundException e -> ErrorType.NOT_FOUND;
            case AccountBlockedException e -> ErrorType.FORBIDDEN;
            case InsufficientFundsException e -> ErrorType.BAD_REQUEST;
            case InvalidOperationException e -> ErrorType.BAD_REQUEST;
            case ConstraintViolationException e -> ErrorType.BAD_REQUEST;
            default -> null;
        };
        if (type == null) {
            return null;
        }
        return GraphqlErrorBuilder.newError(env).errorType(type).message(ex.getMessage()).build();
    }
}
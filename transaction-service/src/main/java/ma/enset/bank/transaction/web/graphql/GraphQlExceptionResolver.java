package ma.enset.bank.transaction.web.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import ma.enset.bank.transaction.service.exception.AccountServiceUnavailableException;
import ma.enset.bank.transaction.service.exception.TransactionNotFoundException;
import ma.enset.bank.transaction.service.exception.TransactionRuleException;
import ma.enset.bank.transaction.service.exception.TransferCompensationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType type = switch (ex) {
            case TransactionNotFoundException e -> ErrorType.NOT_FOUND;
            case TransactionRuleException e -> switch (e.getViolation()) {
                case ACCOUNT_NOT_FOUND -> ErrorType.NOT_FOUND;
                case ACCOUNT_NOT_ACTIVE -> ErrorType.FORBIDDEN;
                default -> ErrorType.BAD_REQUEST;
            };
            case AccountServiceUnavailableException e -> ErrorType.INTERNAL_ERROR;
            case TransferCompensationException e -> ErrorType.INTERNAL_ERROR;
            case ConstraintViolationException e -> ErrorType.BAD_REQUEST;
            default -> null;
        };
        if (type == null) {
            return null;
        }
        return GraphqlErrorBuilder.newError(env).errorType(type).message(ex.getMessage()).build();
    }
}
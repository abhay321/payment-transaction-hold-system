package com.abhay.payment.api;
import com.abhay.payment.application.exception.IdempotencyConflictException;
import com.abhay.payment.application.exception.TransactionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.net.URI;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(TransactionNotFoundException.class) ProblemDetail notFound(TransactionNotFoundException ex){return problem(HttpStatus.NOT_FOUND,"transaction-not-found",ex.getMessage());}
 @ExceptionHandler(OptimisticLockingFailureException.class) ProblemDetail staleWrite(OptimisticLockingFailureException ex){return problem(HttpStatus.CONFLICT,"concurrent-update","Transaction was updated by another request; reload and retry");}
 @ExceptionHandler(IdempotencyConflictException.class) ProblemDetail conflict(IdempotencyConflictException ex){return problem(HttpStatus.CONFLICT,"idempotency-conflict",ex.getMessage());}
 @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) ProblemDetail invalid(RuntimeException ex){return problem(HttpStatus.UNPROCESSABLE_ENTITY,"invalid-transaction-operation",ex.getMessage());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ProblemDetail validation(MethodArgumentNotValidException ex){return problem(HttpStatus.BAD_REQUEST,"invalid-request","Request validation failed");}
 private ProblemDetail problem(HttpStatus status,String code,String detail){ProblemDetail p=ProblemDetail.forStatusAndDetail(status,detail);p.setType(URI.create("https://payment-hold.local/problems/"+code));p.setTitle(code);return p;}
}
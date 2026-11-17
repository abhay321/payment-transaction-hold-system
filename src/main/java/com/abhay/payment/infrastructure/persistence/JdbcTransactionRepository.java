package com.abhay.payment.infrastructure.persistence;

import com.abhay.payment.domain.event.*;
import com.abhay.payment.domain.model.*;
import com.abhay.payment.domain.repository.TransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcTransactionRepository implements TransactionRepository {
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transactions;
 public JdbcTransactionRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
  this.jdbc=jdbc; this.transactions=transactions;
 }
 @Override public void save(Transaction tx) {
  Objects.requireNonNull(tx);
  transactions.executeWithoutResult(status -> {
   int updated=jdbc.update("UPDATE payment_transaction SET captured_amount=?, held_amount=?, released_amount=?, status=?, updated_at=?, expires_at=? WHERE id=?",
    tx.capturedAmount().amount(),tx.heldAmount().amount(),tx.releasedAmount().amount(),tx.status().name(),
    Timestamp.from(tx.updatedAt()),tx.expiresAt()==null?null:Timestamp.from(tx.expiresAt()),tx.id());
   if(updated==0) jdbc.update("INSERT INTO payment_transaction(id,original_amount,currency,captured_amount,held_amount,released_amount,status,created_at,updated_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
    tx.id(),tx.originalAmount().amount(),tx.originalAmount().currency().getCurrencyCode(),tx.capturedAmount().amount(),
    tx.heldAmount().amount(),tx.releasedAmount().amount(),tx.status().name(),Timestamp.from(tx.createdAt()),
    Timestamp.from(tx.updatedAt()),tx.expiresAt()==null?null:Timestamp.from(tx.expiresAt()));
   jdbc.update("DELETE FROM transaction_event WHERE transaction_id=?",tx.id());
   for(TransactionEvent event:tx.events()) jdbc.update(
    "INSERT INTO transaction_event(transaction_id,event_type,amount,currency,occurred_at,resulting_status) VALUES (?,?,?,?,?,?)",
    tx.id(),event.getClass().getSimpleName(),event.amount().amount(),event.amount().currency().getCurrencyCode(),
    Timestamp.from(event.occurredAt()),event.resultingStatus().name());
  });
 }
 @Override public Optional<Transaction> findById(UUID id) {
  List<Transaction> found=jdbc.query("SELECT * FROM payment_transaction WHERE id=?",(rs,n)->{
   String currency=rs.getString("currency");
   Money original=Money.of(rs.getBigDecimal("original_amount"),Currency.getInstance(currency));
   List<TransactionEvent> history=jdbc.query("SELECT * FROM transaction_event WHERE transaction_id=? ORDER BY id",
    (er,i)->toEvent(id,er.getString("event_type"),
     Money.of(er.getBigDecimal("amount"),Currency.getInstance(er.getString("currency"))),
     er.getTimestamp("occurred_at").toInstant(),TransactionStatus.valueOf(er.getString("resulting_status"))),id);
   Timestamp expiry=rs.getTimestamp("expires_at");
   return Transaction.restore(id,original,
    Money.of(rs.getBigDecimal("captured_amount"),Currency.getInstance(currency)),
    Money.of(rs.getBigDecimal("held_amount"),Currency.getInstance(currency)),
    Money.of(rs.getBigDecimal("released_amount"),Currency.getInstance(currency)),
    TransactionStatus.valueOf(rs.getString("status")),rs.getTimestamp("created_at").toInstant(),
    rs.getTimestamp("updated_at").toInstant(),expiry==null?null:expiry.toInstant(),history);
  },id);
  return found.stream().findFirst();
 }
 private TransactionEvent toEvent(UUID id,String type,Money amount,Instant at,TransactionStatus status){
  return switch(type){
   case "TransactionCreated" -> new TransactionCreated(id,amount,at);
   case "HoldPlaced" -> new HoldPlaced(id,amount,at);
   case "AmountCaptured" -> new AmountCaptured(id,amount,at,status);
   case "AmountReleased" -> new AmountReleased(id,amount,at,status);
   case "TransactionExpired" -> new TransactionExpired(id,amount,at);
   default -> throw new IllegalStateException("Unknown persisted event type: "+type);
  };
 }
}

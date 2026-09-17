package com.banking.core_banking_system.account;

import com.banking.core_banking_system.shared.money.CurrencyMismatchException;
import com.banking.core_banking_system.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST layer tối giản - tồn tại chủ yếu để chứng minh Pessimistic Locking (withdraw)
 * hoạt động đúng khi nhiều Pod K8s cùng nhận request cho 1 account, qua HTTP thật
 * thay vì gọi thẳng service trong cùng JVM như test hiện có.
 */
@RestController
public class AccountController {

  private static final Logger log = LoggerFactory.getLogger(AccountController.class);

  // K8s tự set biến môi trường HOSTNAME = tên Pod. Dùng để log rõ request nào
  // được Pod nào xử lý - bằng chứng bắt buộc khi verify Pessimistic Locking
  // qua nhiều Pod (không chỉ tin "không overdraft" mà phải biết request có
  // thực sự rơi vào nhiều Pod khác nhau hay không).
  private static final String POD_NAME = System.getenv().getOrDefault("HOSTNAME", "unknown-pod");

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @PostMapping("/accounts")
  public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
    Account account = accountService.createAccount(request.accountNumber(), request.accountType(), request.currency());
    return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
  }

  @GetMapping("/accounts/{id}/balance")
  public BalanceResponse getBalance(@PathVariable UUID id) {
    return BalanceResponse.of(id, accountService.getBalance(id));
  }

  @PostMapping("/accounts/{id}/deposit")
  public BalanceResponse deposit(@PathVariable UUID id, @RequestBody TransferRequest request) {
    Money amount = Money.of(request.amount(), request.currency());
    accountService.deposit(id, request.counterpartyAccountId(), amount, request.createdBy());
    return BalanceResponse.of(id, accountService.getBalance(id));
  }

  @PostMapping("/accounts/{id}/withdraw")
  public BalanceResponse withdraw(@PathVariable UUID id, @RequestBody TransferRequest request) {
    Money amount = Money.of(request.amount(), request.currency());
    log.info("[pod={}] withdraw REQUEST account={} amount={}", POD_NAME, id, amount);
    accountService.withdraw(id, request.counterpartyAccountId(), amount, request.createdBy());
    BalanceResponse response = BalanceResponse.of(id, accountService.getBalance(id));
    log.info("[pod={}] withdraw SUCCESS account={} newBalance={}", POD_NAME, id, response.amount());
    return response;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(CurrencyMismatchException.class)
  public ResponseEntity<ErrorResponse> handleCurrencyMismatch(CurrencyMismatchException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleInsufficientBalance(IllegalStateException e) {
    log.info("[pod={}] withdraw REJECTED: {}", POD_NAME, e.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
  }
}

package com.banking.core_banking_system.account;

import com.banking.core_banking_system.shared.money.CurrencyMismatchException;
import com.banking.core_banking_system.shared.money.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Tài khoản", description = "Tạo tài khoản, nạp/rút tiền, xem số dư. Mọi thay đổi số dư đều được ghi thành bút toán kép (Nợ = Có) trong ledger.")
public class AccountController {

  private static final Logger log = LoggerFactory.getLogger(AccountController.class);

  // K8s tự set biến môi trường HOSTNAME = tên Pod. Dùng để log rõ request nào
  // được Pod nào xử lý - bằng chứng bắt buộc khi verify Pessimistic Locking
  // qua nhiều Pod (không chỉ tin "không overdraft" mà phải biết request có
  // thực sự rơi vào nhiều Pod khác nhau hay không).
  private static final String POD_NAME = System.getenv().getOrDefault("HOSTNAME", "unknown-pod");

  private static final String TRANSFER_EXAMPLE = """
    {
      "counterpartyAccountId": "<id tài khoản đối ứng, VD quỹ tiền mặt>",
      "amount": 100000,
      "currency": "VND",
      "createdBy": "an"
    }
    """;

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @Operation(
    summary = "Tạo tài khoản",
    description = "Tạo tài khoản mới ở trạng thái ACTIVE. Để nạp/rút được tiền cần ít nhất 2 tài khoản "
      + "(VD: 1 tài khoản khách hàng + 1 tài khoản quỹ tiền mặt làm phía đối ứng).",
    requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
      @ExampleObject(name = "Tài khoản khách hàng", value = """
        {"accountNumber": "AN001", "accountType": "CHECKING", "currency": "VND"}"""),
      @ExampleObject(name = "Quỹ tiền mặt", value = """
        {"accountNumber": "CASH001", "accountType": "CASH", "currency": "VND"}""")
    }))
  )
  @ApiResponse(responseCode = "201", description = "Tạo thành công, trả về id của tài khoản")
  @PostMapping("/accounts")
  public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
    Account account = accountService.createAccount(request.accountNumber(), request.accountType(), request.currency());
    return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
  }

  @Operation(
    summary = "Xem số dư",
    description = "Số dư = tổng Có − tổng Nợ của mọi bút toán thuộc tài khoản. Đọc từ Redis nếu có, "
      + "nếu không thì tính từ ledger rồi lưu lại vào Redis."
  )
  @ApiResponse(responseCode = "200", description = "Số dư hiện tại")
  @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/accounts/{id}/balance")
  public BalanceResponse getBalance(@Parameter(description = "id của tài khoản") @PathVariable UUID id) {
    return BalanceResponse.of(id, accountService.getBalance(id));
  }

  @Operation(
    summary = "Nạp tiền",
    description = "Ghi 1 giao dịch 2 bút toán: tài khoản {id} được ghi CÓ, tài khoản đối ứng bị ghi NỢ cùng số tiền. "
      + "Không khóa tài khoản vì nạp tiền không thể làm số dư âm.",
    requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
      examples = @ExampleObject(name = "Nạp 100.000 VND", value = TRANSFER_EXAMPLE)))
  )
  @ApiResponse(responseCode = "200", description = "Nạp thành công, trả về số dư mới")
  @ApiResponse(responseCode = "400", description = "Loại tiền không khớp với tài khoản", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/accounts/{id}/deposit")
  public BalanceResponse deposit(@Parameter(description = "id tài khoản nhận tiền") @PathVariable UUID id,
    @RequestBody TransferRequest request) {
    Money amount = Money.of(request.amount(), request.currency());
    accountService.deposit(id, request.counterpartyAccountId(), amount, request.createdBy());
    return BalanceResponse.of(id, accountService.getBalance(id));
  }

  @Operation(
    summary = "Rút tiền",
    description = "Khóa tài khoản (SELECT ... FOR UPDATE), tính số dư trực tiếp từ ledger (không đọc Redis), "
      + "nếu đủ tiền thì ghi 1 giao dịch: tài khoản {id} bị ghi NỢ, tài khoản đối ứng được ghi CÓ.",
    requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
      examples = @ExampleObject(name = "Rút 100.000 VND", value = TRANSFER_EXAMPLE)))
  )
  @ApiResponse(responseCode = "200", description = "Rút thành công, trả về số dư mới")
  @ApiResponse(responseCode = "400", description = "Loại tiền không khớp với tài khoản", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "409", description = "Không đủ số dư", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/accounts/{id}/withdraw")
  public BalanceResponse withdraw(@Parameter(description = "id tài khoản bị trừ tiền") @PathVariable UUID id,
    @RequestBody TransferRequest request) {
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

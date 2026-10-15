# Daily Rupi — Requirements

These are the rules the backend must keep. Code review, both human and AI, cites them by ID.

Sources: the README's "Rules worth knowing" and "Security at a glance" sections, checked against the code on 2026-10-15. The "Enforced in" column says where each rule lives today, so a reviewer can check whether a change still goes through that code.

Every ID is stable. Change a rule's wording only when the behaviour really changes, and never reuse an ID.

## Expenses

| ID | Requirement | Enforced in |
| --- | --- | --- |
| REQ-EXP-001 | An expense `amount` must be greater than zero and have at most 2 decimal places, on **every** API that sets or changes an amount. An invalid amount gets HTTP 400 (`VALIDATION_FAILED`). | `ExpenseDtos.ExpenseRequest` (`@DecimalMin("0.01")`, `@Digits(integer = 10, fraction = 2)`), `@Valid` in `ExpenseController`; test `ExpenseAndStatusTest.futureDatesAndBadAmountsAreRejected` |
| REQ-EXP-002 | `spentAt` must not be in the future, with 1 minute of tolerance for clock skew. Earlier dates are allowed. A future date gets HTTP 400 (`FUTURE_DATE`). | `ExpenseService.requireNotFuture`, called by `create` and `update` |
| REQ-EXP-003 | A new expense cannot use an inactive item, an item under an inactive sub category or category, or an inactive payment method (HTTP 400). An existing expense may keep an item or payment method that has since become inactive. Only a newly chosen one must be active. | `ExpenseService.requireUsableItem`, `requireUsablePaymentMethod`, and the "only if changed" checks in `update` |
| REQ-EXP-004 | A create that repeats a `clientId` already saved returns the saved expense instead of creating a second one. A `clientId` whose expense was deleted gets HTTP 410 (`DELETED`). | `ExpenseService.create`; `ExpenseSyncTest` |
| REQ-EXP-005 | `note` is optional (at most 255 characters). It is trimmed, and a blank note is stored as `null`. Any code that reads a note must handle its absence. | `ExpenseService.apply`; `V3__expenses.sql` (`note VARCHAR(255) NULL`) |

## Budgets

| ID | Requirement | Enforced in |
| --- | --- | --- |
| REQ-BUD-001 | A category has at most one budget per month. A budget amount follows the same rules as REQ-EXP-001. | `V4__budgets.sql` (`uk_budgets_category_month`), `BudgetDtos.BudgetRequest`, `BudgetService.set` |
| REQ-BUD-002 | An inactive category keeps any budget it already has but cannot be given a new one (HTTP 400 `INACTIVE_CATEGORY`). Copying last month's budgets skips inactive categories and never overwrites a budget already set. | `BudgetService.set`, `BudgetService.copyFromPreviousMonth` |

## Master data

| ID | Requirement | Enforced in |
| --- | --- | --- |
| REQ-MD-001 | An item with expenses recorded against it cannot be deleted, only made inactive. A category or sub category cannot be deleted while it still has children. Refusals get HTTP 409 (`IN_USE`). | `MasterDataService.deleteItem`, `deleteSubCategory`, `deleteCategory`; foreign keys in `V2__master_data.sql` and `V3__expenses.sql` |

## Security

| ID | Requirement | Enforced in |
| --- | --- | --- |
| REQ-SEC-001 | All SQL uses bind parameters (Spring Data derived queries, JPQL named parameters, or `?` placeholders). Request data is never concatenated into SQL. | All repositories today use derived queries or JPQL with named parameters (`ExpenseRepository.sumBetween`, `BudgetRepository`) |
| REQ-SEC-002 | Deny by default. Without logging in, only `GET /api/auth/csrf` and the login form post (`POST /api/auth/login`) are reachable. The rest of `/api/auth/**` needs a logged-in user, `/api/admin/**` needs ADMIN, and the rest of `/api/**` needs USER. Anything else is refused. Every write needs a CSRF token. | `SecurityConfig.securityFilterChain`; `AuthFlowTest`, `MasterDataTest` |
| REQ-SEC-003 | Error responses never include stack traces or internal exception messages. Errors are returned as `ApiError` (`code`, `message`). | `GlobalExceptionHandler`; `server.error.*` in `application.yml` |

## API compatibility

| ID | Requirement | Enforced in |
| --- | --- | --- |
| REQ-API-001 | Response field names and JSON types used by the Angular frontend (`frontend/src/app/core/api.ts`, `core/auth.service.ts`) and by the Android app's sync (`GET /api/expenses/changes`) are a published contract. Fields may be added. Removing, renaming, or changing the type of a contracted field needs a versioned API. | `backend/src/test/resources/contracts/consumers/*.json`, checked by `ConsumerContractTest` (`@Tag("contract")`) |

# 딥파인 사전 과제

재고 관리 시스템 MVP

## 기능

- 상품 입고 API: 상품의 재고 수량을 증가시킵니다. 등록되지 않은 상품은 신규 상품으로 등록 후 입고 처리
- 상품 출고 API: 상품 재고 수량을 감소시킵니다. 재고 수량이 음수가 되지 않게 처리
- 현재 재고 조회 API: 상품 재고 수량 조회

## 기술 스택

- Java 21, Spring Boot 4.1.1, JPA, PostgreSQL 17, k6

## 스키마

- [재고 관리 시스템 스키마](src/main/resources/db/migration/V1__create_inventory_schema.sql)

### `products` — 상품 기본 정보

| 컬럼         | 타입           | 키·제약조건                        | 설명           |
| ------------ | -------------- | ---------------------------------- | -------------- |
| `id`         | `INT`          | PK, 자동 생성                      | 상품 식별자    |
| `sku`        | `VARCHAR(64)`  | UNIQUE, NOT NULL, 공백만 입력 불가 | 상품 관리 코드 |
| `name`       | `VARCHAR(200)` | NOT NULL, 공백만 입력 불가         | 상품명         |
| `created_at` | `TIMESTAMPTZ`  | NOT NULL                           | 상품 등록 시각 |
| `updated_at` | `TIMESTAMPTZ`  | NOT NULL                           | 상품 수정 시각 |

### `inventory_balances` — 상품별 현재 재고

| 컬럼         | 타입          | 키·제약조건                    | 설명                                       |
| ------------ | ------------- | ------------------------------ | ------------------------------------------ |
| `id`         | `INT`         | PK, 자동 생성                  | 현재 재고 식별자                           |
| `product_id` | `INT`         | FK → `products.id`, NOT NULL   | 재고가 속한 상품. 상품당 현재 재고 행 하나 |
| `quantity`   | `INT`         | NOT NULL, 기본값 `0`, `0` 이상 | 현재 재고 수량                             |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL                       | 수정일                                     |

### `inventory_movements` — 입출고 이력

| 컬럼                   | 타입           | 키·제약조건                            | 설명                |
| ---------------------- | -------------- | -------------------------------------- | ------------------- |
| `id`                   | `BIGINT`       | PK, 자동 생성                          | 입출고 이력 식별자  |
| `inventory_balance_id` | `INT`          | FK → `inventory_balances.id`, NOT NULL | 변경 대상 현재 재고 |
| `movement_type`        | `VARCHAR(16)`  | NOT NULL, `INBOUND` 또는 `OUTBOUND`    | 입고·출고 구분      |
| `quantity`             | `INT`          | NOT NULL, `0` 초과                     | 입고·출고 수량      |
| `balance_after`        | `INT`          | NOT NULL, `0` 이상                     | 처리 후 재고 수량   |
| `reason`               | `VARCHAR(500)` | 선택                                   | 입고·출고 사유      |
| `created_at`           | `TIMESTAMPTZ`  | NOT NULL                               | 입출고 처리 시각    |

## 실행 방법

- 필요 환경: Docker Compose
- `docker compose up -d --build`: PostgreSQL과 애플리케이션 실행. 시작 시 Flyway가 스키마를 적용
- API 확인: [Swagger UI](http://localhost:8080/swagger-ui.html)

## API

| 기능 | 요청                               | 결과              |
| ---- | ---------------------------------- | ----------------- |
| 입고 | `POST /api/v1/inventory/inbounds`  | 입고 후 재고 반환 |
| 출고 | `POST /api/v1/inventory/outbounds` | 출고 후 재고 반환 |
| 조회 | `GET /api/v1/inventory/{sku}`      | 현재 재고 반환    |

- 요청·응답 형식과 직접 실행은 Swagger UI에서 확인
- 잘못된 요청 `400`, 미등록 SKU `404`, 재고 부족·상품명 불일치 `409`

### 테스트 예시

1. 상품 입고 요청 API 데이터 예시

   ```json
   {
    "sku": "DF-DEMO-001",
    "name": "상품 A",
    "quantity": 5,
    "reason": "초기 입고"
   }
   ```

2. 상품 출고 요청 API 데이터 예시

   ```json
   {
    "sku": "DF-DEMO-001",
    "quantity": 2,
    "reason": "주문 출고"
   }
   ```

3. 재고 조회 요청 API `sku` 데이터 예시

   ```text
   DF-DEMO-001
   ```

## 동시성 제어와 정합성

| 문제                      | 처리 방법                                                                              |
| ------------------------- | -------------------------------------------------------------------------------------- |
| 같은 SKU의 동시 최초 입고 | SKU의 `UNIQUE` 제약조건과 `INSERT ... ON CONFLICT DO NOTHING`으로 상품 중복 등록 방지  |
| 같은 SKU의 동시 입출고    | 상품 행에 비관적 락을 적용해 동시성을 제어하고 재고 갱신 누락 방지                     |
| 음수 재고                 | 출고 전 재고 부족 검사와 DB의 `CHECK (quantity >= 0)` 제약조건으로 음수 재고 저장 방지 |
| 재고와 이력의 불일치      | 재고 변경과 입출고 이력 저장을 한 트랜잭션으로 처리해 함께 커밋/롤백                   |

## 적용 도구

- **Flyway:** 버전별 DDL 적용·관리. 앱 시작 시 미적용 마이그레이션 실행
- **Swagger UI:** API 문서화 및 요청·응답 형식 확인

## 부하 테스트

- k6 스크립트: [`performance/inventory-load.js`](performance/inventory-load.js)
- 같은 상품에 요청이 몰릴 때와 20개 상품에 나뉘어 들어올 때의 입고 요청 비교
- 테스트 시작 전 새 상품을 등록하고, 반복마다 수량 1을 입고
- 부하 종료 후 완료된 입고 건수만큼 재고가 증가했는지 k6에서 확인
- 확인 지표: 실제 전송한 입고 요청 수, 응답 지연 p95, HTTP 실패율, 최종 재고

테스트할 앱과 PostgreSQL은 `docker compose up -d --build`로 실행합니다.

### k6 설치

```bash
# macOS의 경우
brew install k6
```

### 실행 방법

| 환경변수    | 의미                                                      | 기본값                  |
| ----------- | --------------------------------------------------------- | ----------------------- |
| `DURATION`  | 입고 요청을 보내는 시간. 초 단위로 입력 (`10s` 등)        | `10s`                   |
| `RATE`      | 1초에 시작할 입고 요청의 목표 건수                        | `50`                    |
| `VUS`       | 요청을 실행할 k6 가상 사용자 수의 상한                    | `100`                   |
| `SKU_COUNT` | 테스트 전에 등록할 상품 수. `1`이면 한 상품에 요청이 몰림 | `1`                     |
| `BASE_URL`  | 테스트 대상 앱 주소                                       | `http://127.0.0.1:8080` |

```bash
k6 run -e SKU_COUNT=1 -e RATE=1000 -e DURATION=10s -e VUS=500 performance/inventory-load.js
k6 run -e SKU_COUNT=20 -e RATE=3000 -e DURATION=10s -e VUS=500 performance/inventory-load.js
```

각 반복은 입고 요청 1건입니다. 예를 들어 `RATE=1000`, `DURATION=10s`는 입고 10,000건을 목표로 합니다. 최종 재고 조회는 부하가 끝난 뒤 자동으로 실행되므로 전체 실행 시간은 `DURATION`보다 약 5초 깁니다. 테스트 데이터는 `K6-` 접두사의 SKU로 DB에 남습니다.

### 측정 결과

- 서버 자원 한도: 앱 CPU 2개·RAM 2GiB, PostgreSQL CPU 2개·RAM 2GiB
- k6는 같은 머신에서 실행. 각 조건 10초, 최대 가상 사용자 수 500

| 상품 수 | 목표 입고/초 | 실제 전송한 입고 | 입고 응답 p95 | HTTP 실패 |
| ------- | -----------: | ---------------: | ------------: | --------: |
| 1개     |        1,000 |         10,000건 |       49.06ms |       0건 |
| 1개     |        1,500 |         14,103건 |      412.73ms |       0건 |
| 20개    |        3,000 |         30,001건 |      131.57ms |       0건 |

- 전송한 입고 요청은 모두 HTTP 200이었고, 최종 재고도 초기 수량과 성공한 입고 건수의 합과 일치했습니다.
- 이번 측정에서 단일 상품 입고의 응답 시간 p95는 초당 1,000건 조건에서 49.06ms, 초당 1,500건을 목표로 한 조건에서 412.73ms였습니다.

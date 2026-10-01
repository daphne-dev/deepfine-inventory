import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const skuCount = Number(__ENV.SKU_COUNT || 1);
const rate = Number(__ENV.RATE || 50);
const vus = Number(__ENV.VUS || 100);
const duration = __ENV.DURATION || '10s';
const productName = 'K6 테스트 상품';
const initialQuantity = 1;
const durationMatch = /^([1-9]\d*)s$/.exec(duration);

if (!durationMatch) {
	throw new Error('DURATION은 10s처럼 초 단위로 입력해야 합니다.');
}
const verificationStart = `${Number(durationMatch[1]) + 5}s`;

if (!Number.isInteger(skuCount) || skuCount < 1) {
	throw new Error('SKU_COUNT는 양수여야 합니다.');
}
if (!Number.isInteger(rate) || rate < 1) {
	throw new Error('RATE는 양수여야 합니다.');
}
if (!Number.isInteger(vus) || vus < 1) {
	throw new Error('VUS는 양수여야 합니다.');
}

export const options = {
	scenarios: {
		inventory_writes: {
			executor: 'constant-arrival-rate',
			rate,
			timeUnit: '1s',
			duration,
			preAllocatedVUs: vus,
			gracefulStop: '5s',
		},
		verify_inventory: {
			executor: 'per-vu-iterations',
			exec: 'verifyInventory',
			vus: 1,
			iterations: 1,
			startTime: verificationStart,
		},
	},
	thresholds: {
		'checks{phase:load}': ['rate==1'],
		'checks{phase:verify}': ['rate==1'],
	},
};

const headers = { 'Content-Type': 'application/json' };

export function setup() {
	const runId = Date.now().toString(36);
	const skus = [];

	for (let i = 0; i < skuCount; i++) {
		const sku = `K6-${runId}-${i}`;
		const response = http.post(
			`${baseUrl}/api/v1/inventory/inbounds`,
			JSON.stringify({
				sku,
				name: productName,
				quantity: initialQuantity,
				reason: '부하 테스트 초기 재고',
			}),
			{ headers, tags: { phase: 'setup' } },
		);
		if (response.status !== 200) {
			throw new Error(
				`초기 입고 실패: ${sku}, HTTP ${response.status}, ${response.body}`,
			);
		}
		skus.push(sku);
	}

	return { skus };
}

export default function (data) {
	const sku = data.skus[exec.scenario.iterationInTest % data.skus.length];
	const response = http.post(
		`${baseUrl}/api/v1/inventory/inbounds`,
		JSON.stringify({ sku, name: productName, quantity: 1, reason: '부하 테스트 입고' }),
		{ headers, tags: { phase: 'load' }, timeout: '5s' },
	);
	check(
		response,
		{ 'HTTP 200': (result) => result.status === 200 },
		{ phase: 'load' },
	);
}

export function verifyInventory(data) {
	const completed = exec.instance.iterationsCompleted;
	const receiptsPerSku = Math.floor(completed / data.skus.length);
	const remainder = completed % data.skus.length;
	let matched = 0;

	for (let i = 0; i < data.skus.length; i++) {
		const sku = data.skus[i];
		const expected = initialQuantity + receiptsPerSku + (i < remainder ? 1 : 0);
		const response = http.get(`${baseUrl}/api/v1/inventory/${sku}`, {
			tags: { phase: 'verify' },
		});
		const actual = response.status === 200 ? response.json('quantity') : null;
		const valid = actual === expected;
		if (!valid) {
			console.error(`${sku}: 기대=${expected}, 실제=${actual}, HTTP ${response.status}`);
		}
		matched += Number(valid);
		check(valid, { '상품별 최종 재고 일치': (result) => result }, { phase: 'verify' });
	}
	console.log(`상품별 최종 재고 검증: ${matched}/${data.skus.length}개 일치`);
}

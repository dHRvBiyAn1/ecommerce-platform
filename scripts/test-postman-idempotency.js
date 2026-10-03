const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const collection = JSON.parse(fs.readFileSync("ecommerce-platform.postman_collection.json", "utf8"));
const requests = [];
function collect(items) {
  for (const item of items) item.item ? collect(item.item) : requests.push(item);
}
collect(collection.item);

const cases = [
  ["Create order (idempotent)", "order_idempotency_key"],
  ["Create payment (idempotent)", "payment_idempotency_key"],
  ["Refund payment (admin, idempotent)", "refund_idempotency_key"],
  ["9 · Create order", "e2e_order_idempotency_key"],
  ["10 · Create payment", "e2e_payment_idempotency_key"],
];
const variables = new Map(collection.variable.map(({ key, value }) => [key, value]));
let generated = 0;
const pm = {
  collectionVariables: {
    get: (key) => variables.get(key),
    set: (key, value) => variables.set(key, value),
  },
  variables: {
    replaceIn: (template) => {
      assert.equal(template, "{{$guid}}");
      return `node-vm-guid-${++generated}`;
    },
  },
};

const firstValues = [];
for (const [name, variableName] of cases) {
  const request = requests.find((item) => item.name === name);
  assert.ok(request, `missing Postman request: ${name}`);
  assert.ok(variables.has(variableName), `missing collection variable: ${variableName}`);
  const header = request.request.header.find((item) => item.key === "X-Idempotency-Key");
  assert.equal(header?.value, `{{${variableName}}}`, `${name} must use its own collection key`);
  const script = request.event?.find((event) => event.listen === "prerequest")?.script.exec.join("\n");
  assert.ok(script, `${name} needs a prerequest key initializer`);

  vm.runInNewContext(script, { pm });
  const first = variables.get(variableName);
  vm.runInNewContext(script, { pm });
  assert.equal(variables.get(variableName), first, `${name} changed its key on a repeated send`);
  firstValues.push(first);
}
assert.equal(new Set(firstValues).size, cases.length, "each logical operation needs a distinct key");

for (const [name, variableName] of cases) {
  const before = variables.get(variableName);
  variables.delete(variableName);
  const request = requests.find((item) => item.name === name);
  const script = request.event.find((event) => event.listen === "prerequest").script.exec.join("\n");
  vm.runInNewContext(script, { pm });
  const rotated = variables.get(variableName);
  assert.notEqual(rotated, before, `${name} did not rotate after explicit reset`);
  vm.runInNewContext(script, { pm });
  assert.equal(variables.get(variableName), rotated, `${name} changed its rotated key on retry`);
}

console.log("PASS: Postman idempotency keys are stable on retry, distinct per operation, and rotate after reset");

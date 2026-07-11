import { describe,expect,it } from 'vitest';import { requestKey } from './id'
describe('requestKey',()=>it('creates UUID idempotency keys',()=>expect(requestKey()).toMatch(/^[0-9a-f-]{36}$/i)))

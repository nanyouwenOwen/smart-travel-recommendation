import http from 'k6/http';
import { check } from 'k6';
export const options={vus:10,duration:'30s',thresholds:{http_req_failed:['rate<0.01'],http_req_duration:['p(95)<500']}};
export default function(){const health=http.get(`${__ENV.BASE_URL}/api/v1/health`);check(health,{'health 200':x=>x.status===200});const page=http.get(`${__ENV.BASE_URL}/`);check(page,{'frontend 200':x=>x.status===200&&x.body.includes('旅途智囊')});}

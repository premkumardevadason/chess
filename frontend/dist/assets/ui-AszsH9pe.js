import{r as f}from"./router-sXP0cy3I.js";function C(e){var r,t,n="";if(typeof e=="string"||typeof e=="number")n+=e;else if(typeof e=="object")if(Array.isArray(e)){var o=e.length;for(r=0;r<o;r++)e[r]&&(t=C(e[r]))&&(n&&(n+=" "),n+=t)}else for(t in e)e[t]&&(n&&(n+=" "),n+=t);return n}function N(){for(var e,r,t=0,n="",o=arguments.length;t<o;t++)(e=arguments[t])&&(r=C(e))&&(n&&(n+=" "),n+=r);return n}const h=e=>typeof e=="boolean"?`${e}`:e===0?"0":e,k=N,x=(e,r)=>t=>{var n;if(r?.variants==null)return k(e,t?.class,t?.className);const{variants:o,defaultVariants:l}=r,v=Object.keys(o).map(a=>{const s=t?.[a],u=l?.[a];if(s===null)return null;const i=h(s)||h(u);return o[a][i]}),c=t&&Object.entries(t).reduce((a,s)=>{let[u,i]=s;return i===void 0||(a[u]=i),a},{}),d=r==null||(n=r.compoundVariants)===null||n===void 0?void 0:n.reduce((a,s)=>{let{class:u,className:i,...g}=s;return Object.entries(g).every(w=>{let[y,m]=w;return Array.isArray(m)?m.includes({...l,...c}[y]):{...l,...c}[y]===m})?[...a,u,i]:a},[]);return k(e,v,d,t?.class,t?.className)};/**
 * @license lucide-react v0.294.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */var A={xmlns:"http://www.w3.org/2000/svg",width:24,height:24,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:2,strokeLinecap:"round",strokeLinejoin:"round"};/**
 * @license lucide-react v0.294.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const V=e=>e.replace(/([a-z0-9])([A-Z])/g,"$1-$2").toLowerCase().trim(),b=(e,r)=>{const t=f.forwardRef(({color:n="currentColor",size:o=24,strokeWidth:l=2,absoluteStrokeWidth:v,className:c="",children:d,...a},s)=>f.createElement("svg",{ref:s,...A,width:o,height:o,stroke:n,strokeWidth:v?Number(l)*24/Number(o):l,className:["lucide",`lucide-${V(e)}`,c].join(" "),...a},[...r.map(([u,i])=>f.createElement(u,i)),...Array.isArray(d)?d:[d]]));return t.displayName=`${e}`,t};/**
 * @license lucide-react v0.294.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const $=b("Check",[["path",{d:"M20 6 9 17l-5-5",key:"1gmf2c"}]]);/**
 * @license lucide-react v0.294.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const L=b("X",[["path",{d:"M18 6 6 18",key:"1bl5f8"}],["path",{d:"m6 6 12 12",key:"d8bk6v"}]]);export{$ as C,L as X,x as a,N as c};
//# sourceMappingURL=ui-AszsH9pe.js.map

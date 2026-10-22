// So em desenvolvimento, carregado pelo layout Thymeleaf direto do servidor do
// Vite. O @vitejs/plugin-react exige este preambulo para o hot reload; o
// index.html do Vite (painel) o injeta sozinho, mas as paginas publicas vem do Spring.
// @ts-expect-error modulo virtual servido pelo Vite em dev
import RefreshRuntime from '/@react-refresh';

RefreshRuntime.injectIntoGlobalHook(window);
const global = window as unknown as Record<string, unknown>;
global.$RefreshReg$ = () => {};
global.$RefreshSig$ = () => (type: unknown) => type;
global.__vite_plugin_react_preamble_installed__ = true;

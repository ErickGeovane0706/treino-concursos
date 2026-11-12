// So nos testes das ilhas: "react" e "react-dom" viram o preact/compat, como em producao.
// O act vem do preact/test-utils, porque a Testing Library usa React.act quando existe.
export * from 'preact/compat';
export { default } from 'preact/compat';
export { act } from 'preact/test-utils';

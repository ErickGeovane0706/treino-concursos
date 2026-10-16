import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import '../estilos.css';

/**
 * Monta uma ilha React sobre o elemento que a pagina Thymeleaf marcou com
 * data-ilha="<nome>". O HTML do servidor continua valendo sem JavaScript:
 * se o elemento nao existir, nada acontece.
 */
export function montarIlha(nome: string, conteudo: ReactNode): void {
  const elemento = document.querySelector<HTMLElement>(`[data-ilha="${nome}"]`);
  if (!elemento) return;
  createRoot(elemento).render(<StrictMode>{conteudo}</StrictMode>);
}

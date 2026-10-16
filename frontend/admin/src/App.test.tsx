import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App do painel', () => {
  it('renderiza o título', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Painel do Treino de Concursos' })).toBeInTheDocument();
  });
});

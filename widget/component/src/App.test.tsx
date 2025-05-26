import { describe, it, expect } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import App from './App';
import '@testing-library/jest-dom';

describe('App', () => {
  it('renders without crashing', async () => {
    await act(async () => {
      render(<App />);
    });
    expect(screen.getByText(/Varsler/i)).toBeInTheDocument();
  });
});

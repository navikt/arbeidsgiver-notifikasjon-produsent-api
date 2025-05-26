import { describe, it, expect } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import App from './App';
import '@testing-library/jest-dom';
import { BrowserRouter } from 'react-router';

describe('App', () => {
  it('renders without crashing', async () => {
    await act(async () => {
      render(
        <BrowserRouter>
          <App />
        </BrowserRouter>,
      );
    });
    expect(screen.getByText(/Varsler/i)).toBeInTheDocument();
  });
});

/** The value `hello` returns. Scaffold check for ORC-15, removed in ORC-16. */
export interface Hello {
  greeting: string;
  abilities: string[];
}

/** Scaffold check for ORC-15, removed in ORC-16. */
export function hello(): Hello;

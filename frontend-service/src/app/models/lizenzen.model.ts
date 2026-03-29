export interface LizenzHash {
  algorithm: string;
  value: string;
}

export interface Lizenz {
  name: string;
  version: string | null;
  license: string;
  publisher: string | null;
  url: string | null;
  hashes: LizenzHash[];
}

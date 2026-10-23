#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const packagePath = path.join(root, 'package.json');
const lockPath = path.join(root, 'package-lock.json');

function fail(message) {
  console.error(`Lockfile validation failed: ${message}`);
  process.exit(1);
}

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (error) {
    fail(`cannot read ${path.relative(root, file)}: ${error.message}`);
  }
}

if (!fs.existsSync(lockPath)) {
  fail('package-lock.json is missing; generate it with npm install --package-lock-only and commit it.');
}

const manifest = readJson(packagePath);
const lock = readJson(lockPath);
if (lock.lockfileVersion !== 3) {
  fail(`expected npm lockfileVersion 3, got ${String(lock.lockfileVersion)}`);
}
if (lock.name !== manifest.name || lock.version !== manifest.version) {
  fail('package name/version differs from package.json.');
}

const rootPackage = lock.packages && lock.packages[''];
if (!rootPackage) fail('packages[""] entry is missing.');

for (const section of ['dependencies', 'devDependencies', 'optionalDependencies']) {
  const expected = manifest[section] || {};
  const actual = rootPackage[section] || {};
  for (const [name, range] of Object.entries(expected)) {
    if (actual[name] !== range) {
      fail(`${section} mismatch for ${name}: package.json=${range}, lockfile=${String(actual[name])}`);
    }
  }
  for (const name of Object.keys(actual)) {
    if (!(name in expected)) fail(`${section} contains undeclared root dependency ${name}`);
  }
}

console.log('package.json and npm lockfile root metadata: OK');

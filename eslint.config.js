const tsParser=require('@typescript-eslint/parser');
const tsPlugin=require('@typescript-eslint/eslint-plugin');
module.exports=[{ignores:['node_modules/**','.expo/**','coverage/**','android/**','ios/**']},{files:['**/*.ts','**/*.tsx'],languageOptions:{parser:tsParser,parserOptions:{ecmaVersion:'latest',sourceType:'module',ecmaFeatures:{jsx:true}}},plugins:{'@typescript-eslint':tsPlugin},rules:{'no-eval':'error','no-implied-eval':'error','no-new-func':'error','no-console':['error',{allow:['warn','error']}],'@typescript-eslint/no-explicit-any':'error'}}];

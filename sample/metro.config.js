/**
 * Metro configuration for React Native
 * https://github.com/facebook/react-native
 *
 * @format
 */
const path = require('path')
const fs = require('fs')

const root = path.resolve(__dirname, '..');
const pak = JSON.parse(
  fs.readFileSync(path.join(root, 'package.json'), 'utf8')
);

const modules = [
  '@babel/runtime',
  ...Object.keys({
    ...pak.dependencies,
    ...pak.peerDependencies,
  }),
];


module.exports = {
  projectRoot: __dirname,
  watchFolders: [root],
  resolver: {
    extraNodeModules: modules.reduce((acc, name) => {
      acc[name] = path.join(__dirname, 'node_modules', name);
      return acc;
    }, {}),
  },
  transformer: {
    getTransformOptions: async () => ({
      transform: {
        experimentalImportSupport: false,
        inlineRequires: true,
      },
    }),
  },
};
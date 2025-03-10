function safe_require(module_name) {
  try {
    return require(module_name);
  } catch (e) {
    console.log('Error: nodejs module ' + module_name +
      ' must be installed (you might want to try: npm install ' + module_name + ')');
    process.exit(1);
  }
}

// `Wasm` does **not** understand node buffers, but thankfully a node buffer
// is easy to convert to a native Uint8Array.
function toUint8Array(buf) {
  const u = new Uint8Array(buf.length);
  for (let i = 0; i < buf.length; ++i) {
    u[i] = buf[i];
  }
  return u;
}
// Loads a WebAssembly dynamic library, returns a promise.
// imports is an optional imports object
function loadWebAssembly(filename, imports) {
  // Fetch the file and compile it
  const buffer = toUint8Array(require('fs').readFileSync(filename))
  return WebAssembly.compile(buffer).then(module => {
    return new WebAssembly.Instance(module, imports)
  })
}

const deasync = safe_require('deasync');
const rl = require('readline').createInterface({
  input: process.stdin,
  output: process.stdout
});

// Newer versions of NodeJS don't seem to buffer line events, so we might lose inputs
// when stdin was redirected (e.g. in the automated tests).
let inputLines = [];
rl.on('line', function(answer) {
  inputLines.push(answer);
});

function waitInput() {
  deasync.loopWhile(function(){return inputLines.length <= 0;});
  return inputLines.shift();
}

const log = console.log;
const exit = () => process.exit(1);

var memory = new WebAssembly.Memory({initial:100});
const importObject = {
  system: {
    mem: memory,

    printInt: function(arg) {
      log(arg);
      0;
    },

    printString: function(arg) {
      var bufView = new Uint8Array(memory.buffer);
      var i = arg;
      var result = "";
      while(bufView[i] != 0) {
       result += String.fromCharCode(bufView[i]);
       i = i + 1;
      }
      log(result);
      0;
    },

    readInt: function() {
      var res = parseInt(waitInput());
      if (isNaN(res)) {
        log("Error: Could not parse int");
        exit();
      } else {
        return res;
      }
    },

    // This function has a weird signature due to restrictions of the current WASM format:
    // It takes as argument the position that it needs to store the string to,
    // and returns the first position after the new string.
    readString0: function(memB) {
      var s = waitInput();
      var size = s.length;
      var padding = 4 - size % 4;
      var fullString = s + " ".repeat(padding);
      var newMemB = memB + size + padding;
      var bufView8 = new Uint8Array(memory.buffer);
      for (var i = 0; i < fullString.length; i++) {
        bufView8[memB + i] = fullString.charCodeAt(i);
      }
      return newMemB;
    }
  }
};


loadWebAssembly('wasmout/Hello.wasm', importObject).then(function(instance) {
  instance.exports.Hello_main();
  rl.close();
}).catch( function(error) {
  rl.close();
  process.exit(1)
})

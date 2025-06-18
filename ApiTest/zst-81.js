atob = function(input) {
    const keyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    var output = "";
    var chr1, chr2, chr3;
    var enc1, enc2, enc3, enc4;
    var i = 0;

    input = input.replace(/[^A-Za-z0-9\+\/=]/g, "");

    do {
        enc1 = keyStr.indexOf(input.charAt(i++));
        enc2 = keyStr.indexOf(input.charAt(i++));
        enc3 = keyStr.indexOf(input.charAt(i++));
        enc4 = keyStr.indexOf(input.charAt(i++));

        chr1 = (enc1 << 2) | (enc2 >> 4);
        chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
        chr3 = ((enc3 & 3) << 6) | enc4;

        output += String.fromCharCode(chr1);

        if (enc3 !== 64) output += String.fromCharCode(chr2);
        if (enc4 !== 64) output += String.fromCharCode(chr3);
    } while (i < input.length);

    return output;
};
var _type_of =
    t = function() {},
    i = function(x) {
        this.s = (2048 & x) >> 11,
        this.t = (1536 & x) >> 9,
        this.i = 511 & x,
        this.h = 511 & x
    },
    h = function(x) {
        this.t = (3072 & x) >> 10,
        this.h = 1023 & x
    },
    B = function(x) {
        this.B = (3072 & x) >> 10,
        this.n = (768 & x) >> 8,
        this.Q = (192 & x) >> 6,
        this.s = 63 & x
    },
    n = function(x) {
        this.t = x >> 10 & 3,
        this.i = 1023 & x
    },
    Q = function() {},
    a = function(x) {
        this.B = (3072 & x) >> 10,
        this.n = (768 & x) >> 8,
        this.Q = (192 & x) >> 6,
        this.s = 63 & x
    },
    C = function(x) {
        this.h = (4095 & x) >> 2,
        this.s = 3 & x
    },
    c = function(x) {
        this.t = x >> 10 & 3,
        this.i = x >> 2 & 255,
        this.s = 3 & x
    },
    o = function(x) {
        this.s = (4095 & x) >> 10,
        this.t = (1023 & x) >> 8,
        this.i = 1023 & x,
        this.h = 63 & x
    },
    k = function(x) {
        this.s = (4095 & x) >> 10,
        this.B = (1023 & x) >> 8,
        this.n = (255 & x) >> 6
    },
    g = function(x) {
        this.t = (3072 & x) >> 10,
        this.h = 1023 & x
    },
    G = function(x) {
    this.h = 4095 & x
    },
    r = function(x) {
    this.t = (3072 & x) >> 10
    },
    w = function(x) {
    this.h = 4095 & x
    },
    E = function(x) {
        this.s = (3840 & x) >> 8,
        this.t = (192 & x) >> 6,
        this.i = 63 & x
    },
    D = function() {
        this.c = [0, 0, 0, 0],
        this.k = 0,
        this.o = [],
        this.e = [],
        this.g = [],
        this.G = [],
        this.r = [],
        this.w = !1,
        this.R = [],
        this.I = [],
        this.C = !1,
        this.D = null,
        this.Y = null,
        this.f = [],
        this.J = 0,
        this.u = {
            0: t,
            1: i,
            2: h,
            3: B,
            4: n,
            5: Q,
            6: a,
            7: C,
            8: c,
            9: o,
            10: k,
            11: g,
            12: G,
            13: r,
            14: w,
            15: E
        }
    };

function s(x) {
    return (s = "function" == typeof Symbol && "symbol" == _type_of._(Symbol.A) ? function(x) {
            return void 0 === x ? "undefined" : _type_of._(x)
        } :
        function(x) {
            return x && "function" == typeof Symbol && x.constructor === Symbol && x !== Symbol.prototype ? "symbol" : void 0 === x ? "undefined" : _type_of._(x)
        }
    )(x)
}
var A = "2.0", __g = {};
t.prototype.a = function(x) {
    x.C = !1
},
i.prototype.a = function(x) {
    switch (this.s) {
        case 0:
            x.c[this.t] = this.i;
            break;
        case 1:
            x.c[this.t] = x.e[this.h]
    }
},
h.prototype.a = function(x) {
    x.e[this.h] = x.c[this.t]
},
B.prototype.a = function(x) {
    switch (this.s) {
        case 0:
            x.c[this.B] = x.c[this.n] + x.c[this.Q];
            break;
        case 1:
            x.c[this.B] = x.c[this.n] - x.c[this.Q];
            break;
        case 2:
            x.c[this.B] = x.c[this.n] * x.c[this.Q];
            break;
        case 3:
            x.c[this.B] = x.c[this.n] / x.c[this.Q];
            break;
        case 4:
            x.c[this.B] = x.c[this.n] % x.c[this.Q];
            break;
        case 5:
            x.c[this.B] = x.c[this.n] == x.c[this.Q];
            break;
        case 6:
            x.c[this.B] = x.c[this.n] >= x.c[this.Q];
            break;
        case 7:
            x.c[this.B] = x.c[this.n] || x.c[this.Q];
            break;
        case 8:
            x.c[this.B] = x.c[this.n] && x.c[this.Q];
            break;
        case 9:
            x.c[this.B] = x.c[this.n] !== x.c[this.Q];
            break;
        case 10:
            x.c[this.B] = s(x.c[this.n]);
            break;
        case 11:
            x.c[this.B] = x.c[this.n] in x.c[this.Q];
            break;
        case 12:
            x.c[this.B] = x.c[this.n] > x.c[this.Q];
            break;
        case 13:
            x.c[this.B] = -x.c[this.n];
            break;
        case 14:
            x.c[this.B] = x.c[this.n] < x.c[this.Q];
            break;
        case 15:
            x.c[this.B] = x.c[this.n] & x.c[this.Q];
            break;
        case 16:
            x.c[this.B] = x.c[this.n] ^ x.c[this.Q];
            break;
        case 17:
            x.c[this.B] = x.c[this.n] << x.c[this.Q];
            break;
        case 18:
            x.c[this.B] = x.c[this.n] >>> x.c[this.Q];
            break;
        case 19:
            x.c[this.B] = x.c[this.n] | x.c[this.Q];
            break;
        case 20:
            x.c[this.B] = !x.c[this.n]
    }
},
n.prototype.a = function(x) {
    x.o.push(x.k),
        x.g.push(x.e),
        x.k = x.c[this.t],
        x.e = [];
    for (var d = 0; d < this.i; d++)
        x.e.unshift(x.G.pop());
    x.r.push(x.G),
        x.G = []
},
Q.prototype.a = function(x) {
    x.k = x.o.pop(),
        x.e = x.g.pop(),
        x.G = x.r.pop()
},
a.prototype.a = function(x) {
    switch (this.s) {
        case 0:
            x.w = x.c[this.B] >= x.c[this.n];
            break;
        case 1:
            x.w = x.c[this.B] <= x.c[this.n];
            break;
        case 2:
            x.w = x.c[this.B] > x.c[this.n];
            break;
        case 3:
            x.w = x.c[this.B] < x.c[this.n];
            break;
        case 4:
            x.w = x.c[this.B] == x.c[this.n];
            break;
        case 5:
            x.w = x.c[this.B] != x.c[this.n];
            break;
        case 6:
            x.w = x.c[this.B];
            break;
        case 7:
            x.w = !x.c[this.B]
    }
},
C.prototype.a = function(x) {
    switch (this.s) {
        case 0:
            x.k = this.h;
            break;
        case 1:
            x.w && (x.k = this.h);
            break;
        case 2:
            x.w || (x.k = this.h);
            break;
        case 3:
            x.k = this.h,
                x.D = null
    }
    x.w = !1
},
c.prototype.a = function(x) {
    switch (this.s) {
        case 0:
            for (var d = [], f = 0; f < this.i; f++)
                d.unshift(x.G.pop());
            x.c[3] = x.c[this.t](d[0], d[1]);
            break;
        case 1:
            for (var v = x.G.pop(), T = [], S = 0; S < this.i; S++)
                T.unshift(x.G.pop());
            x.c[3] = x.c[this.t][v](T[0], T[1]);
            break;
        case 2:
            for (var y = [], O = 0; O < this.i; O++)
                y.unshift(x.G.pop());
            x.c[3] = new x.c[this.t](y[0], y[1])
    }
};
var e = function(x) {
    for (var d = 66, f = [], v = 0; v < x.length; v++) {
        var T = 24 ^ x.charCodeAt(v) ^ d;
        f.push(String.fromCharCode(T)),
            d = T
    }
    return f.join("")
};
o.prototype.a = function(x) {
    switch (this.s) {
        case 0:
            x.G.push(x.c[this.t]);
            break;
        case 1:
            x.G.push(this.i);
            break;
        case 2:
            x.G.push(x.e[this.h]);
            break;
        case 3:
            x.G.push(e(x.I[this.h]))
    }
},
k.prototype.a = function(A) {
    switch (this.s) {
        case 0:
            var s = A.G.pop();
            A.c[this.B] = A.c[this.n][s];
            break;
        case 1:
            var t = A.G.pop(),
                i = A.G.pop();
            A.c[this.n][t] = i;
            break;
        case 2:
            var h = A.G.pop();
            A.c[this.B] = eval(h)
    }
},
g.prototype.a = function(x) {
    x.c[this.t] = e(x.I[this.h])
},
G.prototype.a = function(x) {
    x.D = this.h
},
r.prototype.a = function(x) {
    throw x.c[this.t]
},
w.prototype.a = function(x) {
    var d = this,
        f = [0];
    x.e.forEach(function(x) {
        f.push(x)
    });
    var v = function(v) {
        var T = new D;
        return T.e = f,
            T.e[0] = v,
            T.b(x.R, d.h, x.I, x.f),
            T.c[3]
    };
    v.toString = function() {
            return "() { [native code] }"
        },
        x.c[3] = v
},
E.prototype.a = function(x) {
    switch (this.s) {
        case 0:
            for (var d = {}, f = 0; f < this.i; f++) {
                var v = x.G.pop();
                d[x.G.pop()] = v
            }
            x.c[this.t] = d;
            break;
        case 1:
            for (var T = [], S = 0; S < this.i; S++)
                T.unshift(x.G.pop());
            x.c[this.t] = T
    }
},
D.prototype.x = function(x) {
    for (var d = atob(x), f = d.charCodeAt(0) << 8 | d.charCodeAt(1), v = [], T = 2; T < f + 2; T += 2)
        v.push(d.charCodeAt(T) << 8 | d.charCodeAt(T + 1));
    this.R = v;
    for (var S = [], y = f + 2; y < d.length;) {
        var O = d.charCodeAt(y) << 8 | d.charCodeAt(y + 1),
            U = d.slice(y + 2, y + 2 + O);
        S.push(U),
            y += O + 2
    }
    this.I = S
},
D.prototype.b = function(x, d, f) {
    for (d = d || 0,
        f = f || [],
        this.k = d,
        "string" == typeof x ? this.x(x) : (this.R = x,
            this.I = f),
        this.C = !0,
        this.J = Date.now(); this.C;) {
        var v = this.R[this.k++];
        if ("number" != typeof v)
            break;
        var T = Date.now();
        if (5e8 < T - this.J)
            return;
        this.J = T;
        try {
            this.a(v)
        } catch (x) {
            this.Y = x,
            this.D && (this.k = this.D)
        }
    }
},
D.prototype.a = function(x) {
    var d = (61440 & x) >> 12;
    new this.u[d](x).a(this)
},
(new D).b("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");
var R = function(x) {
    return __g._e2(encodeURI(x))
},
I = function(x) {
    return __g._e1(encodeURI(x))
};

osa = window.localStorage.getItem("osa")
osd = document.cookie.match("(^|[^;]+)\\s*osd" + "\\s*=\\s*([^;]+)")
generate_random_str = function(count) {
     var res = "";
     var chars = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"];
     for (var y = 0; y < count; y++) {
        var random_number = Math.round(Math.random() * (chars.length - 1));
        res += chars[random_number]
     }
     return res;
 }

function encrypt(osa, osd) {
    random_str = generate_random_str(43)
    return R(osa+"#"+osd+"#"+random_str)
}

function test() {
    return R("W1sRBk04BG36ArCCHa_y8wAKQRAKESBP2i6Zpj8ULUnYIpyrOeoNJJ4HvYQfTXK1uA-RytTEvzDUENVFkM2wtVA=#UlsRAk4xBG3-AbmCHavx-gAKRRMDESBL2SeZpjsXJEnYJp-iOeoJJ5cHvYAcRHK1vAyYytTAvDnUENFGmc2wsVM=#cyTIcnsXmLGM9TEdjwjfrLgNMb5lEYM0uav2UzcvEnf")
}

test()
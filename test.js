function sink(v)
{
    console.log(v);
    console.log(v);
}

function source()
{
    return "secret string";
}

function id(x)
{
    return x;
}

var v = source();
var x = id(v);
sink(x);

// calledFromJava ("");
function calledFromJava(secret)
{
    sink (v);
}

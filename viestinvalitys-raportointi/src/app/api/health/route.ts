import {NextResponse} from "next/server";

export async function GET (){
    const greeting = "Hello!"
    const json = {
        greeting
    };

    return NextResponse.json(json);
}
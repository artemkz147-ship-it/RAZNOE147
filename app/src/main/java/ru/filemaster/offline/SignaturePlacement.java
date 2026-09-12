package ru.filemaster.offline;

final class SignaturePlacement {
    int page;
    float x;
    float y;
    float width;
    float height;
    SignaturePlacement(int page,float x,float y,float width,float height){this.page=page;this.x=x;this.y=y;this.width=width;this.height=height;}
    SignaturePlacement copy(){return new SignaturePlacement(page,x,y,width,height);}
}

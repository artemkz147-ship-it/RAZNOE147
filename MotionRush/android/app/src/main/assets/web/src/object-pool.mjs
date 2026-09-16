export class ObjectPool {
  constructor(create, reset = () => {}, max = 48) {
    this.create = create;
    this.reset = reset;
    this.max = max;
    this.free = [];
    this.created = 0;
  }
  acquire() {
    const object = this.free.pop() || (this.created++, this.create());
    this.reset(object, false);
    return object;
  }
  release(object) {
    if (!object) return;
    this.reset(object, true);
    if (this.free.length < this.max) this.free.push(object);
  }
  clear(dispose) {
    if (dispose) for (const object of this.free) dispose(object);
    this.free.length = 0;
  }
}

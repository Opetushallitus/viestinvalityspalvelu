export class FetchError extends Error {
    response: Response;
    constructor(response: Response, message: string = 'error.fetch') {
      super(message);
      // Set the prototype explicitly.
      Object.setPrototypeOf(this, FetchError.prototype);
      this.response = response;
    }
  }
  
  export class PermissionError extends Error {
    constructor(message: string = 'error.access-denied') {
      super(message);
    }
  }
  
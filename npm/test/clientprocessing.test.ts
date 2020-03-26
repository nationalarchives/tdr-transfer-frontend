// import {processFiles} from "../src/clientprocessing";
// import {GraphqlClient} from "../src/graphql";
// //jest.mock('../src/graphql', jest.fn());
//
//const mockMutation = jest.fn();
// jest.mock('../src/graphql', () => {
//     return jest.fn().mockImplementation(() => {
//         return {mutation: mockMutation};
//     });
// });
//
beforeEach(() => jest.resetModules())
//
// test("Returns file ids", () => {
//     ;(GraphqlClient as jest.Mock).mockImplementation(() => {
//         return {
//             mutation: () => {
//                 throw Error("test error")
//             }
//         }
//     });
//
//     const mockGraphQlClient = GraphqlClient;
//     expect(processFiles(mockGraphQlClient, 1, 10)).toBe("xxxxxx")
// }
// )

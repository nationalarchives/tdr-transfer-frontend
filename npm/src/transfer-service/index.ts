

export interface ILoadErrors {
    message: string
}

export interface ICompleteLoadInfo {
    "expectedNumberFiles": number,
    "loadedNumberFiles": number,
    "loadErrors": ILoadErrors[]
}


export class TransferService {
    // upload.url="https://upload.tdr-integration.nationalarchives.gov.uk"
    transferServiceUrl = "https://transfer-service.tdr-integration.nationalarchives.gov.uk"

    async completeLoad(consignmentId: string, bearerAccessToken: string | Error, loadInfo: ICompleteLoadInfo): Promise<Response | Error>{
        const url = `https://transfer-service.tdr-integration.nationalarchives.gov.uk/load/networkdrive/complete/${consignmentId}`
        return await fetch(`${url}`, {
            credentials: "include",
            method: "POST",
            body: JSON.stringify(loadInfo),
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${bearerAccessToken}`,
                "X-Requested-With": "XMLHttpRequest"
            }
        }).catch((err) => {
            return Error(err)
        })
    }
}
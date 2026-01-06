

export interface ILoadErrors {
    message: string
}

export interface ICompleteLoadInfo {
    "expectedNumberFiles": number,
    "loadedNumberFiles": number,
    "loadErrors": ILoadErrors[]
}

export interface ILoadDestination {
    "awsRegion": string,
    "bucketArn": string,
    "bucketName": string,
    "bucketKeyPrefix": string
}

export interface IInitiateLoadInfo {
    "transferId": string,
    "transferReference": string,
    "recordsLoadDestination": ILoadDestination,
    "metadataLoadDestination": ILoadDestination
}


export class TransferService {
    // upload.url="https://upload.tdr-integration.nationalarchives.gov.uk"
    transferServiceUrl = "https://transfer-service.tdr-integration.nationalarchives.gov.uk"

    async initiateLoad(consignmentId: string, bearerAccessToken: string): Promise<Response | Error> {
        const url = `https://transfer-service.tdr-integration.nationalarchives.gov.uk/load/networkdrive/initiate?transfer-id=${consignmentId}`
        return await fetch(`${url}`, {
            credentials: "include",
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${bearerAccessToken}`,
                "X-Requested-With": "XMLHttpRequest"
            }
        }).catch((err) => {
            return Error(err)
        })
    }

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
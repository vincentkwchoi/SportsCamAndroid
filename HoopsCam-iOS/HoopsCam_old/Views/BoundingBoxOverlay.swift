import SwiftUI

struct BoundingBoxOverlay: View {
    let detections: [Detection]
    
    var body: some View {
        GeometryReader { geometry in
            ForEach(detections) { detection in
                let bbox = detection.bbox
                let rect = CGRect(
                    x: bbox.origin.x * geometry.size.width,
                    y: bbox.origin.y * geometry.size.height,
                    width: bbox.size.width * geometry.size.width,
                    height: bbox.size.height * geometry.size.height
                )
                
                Rectangle()
                    .path(in: rect)
                    .stroke(Color.green, lineWidth: 2)
                    .overlay(
                        Text(String(format: "%.2f", detection.confidence))
                            .font(.caption)
                            .foregroundColor(.white)
                            .background(Color.black.opacity(0.7))
                            .position(x: rect.midX, y: rect.minY - 10)
                    )
            }
        }
    }
}
